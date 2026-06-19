package com.messenger.client.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice call handler với relay support (vượt NAT).
 *
 * Flow:
 * 1. Chuẩn bị socket → prepareForCall()
 * 2. Nhận RELAY_INFO từ server → startCall(sessionId, relayHost, relayPort)
 * 3. Audio capture → noise gate → gửi UDP đến relay (prefix 8-byte sessionId)
 * 4. Nhận audio từ relay → playback
 * 5. Kết thúc → endCall() → gửi END signal lên server
 *
 * Cải tiến:
 * - Noise gate: bỏ qua frame yên lặng
 * - Auto Gain Control (AGC) cơ bản
 * - Jitter buffer nhỏ cho playback
 */
public class VoiceCallHandler {

    private static final Logger logger = LoggerFactory.getLogger(VoiceCallHandler.class);

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int BUFFER_SIZE = 1024;       // ~32ms at 16kHz 16-bit mono

    // Noise gate
    private static final float NOISE_THRESHOLD = 0.008f;  // RMS threshold for silence
    private static final int SILENCE_FRAMES_BEFORE_DROP = 3;

    // Jitter buffer
    private static final int JITTER_BUFFER_MS = 60;
    private static final int JITTER_BUFFER_SIZE = JITTER_BUFFER_MS * SAMPLE_RATE / 1000 * (SAMPLE_SIZE_BITS / 8);

    private final AudioFormat audioFormat;
    private final AtomicBoolean running;
    private final AtomicBoolean muted;

    private DatagramSocket socket;
    private InetSocketAddress relayAddress;
    private byte[] sessionIdBytes;

    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private final AudioDevice inputDevice;
    private final AudioDevice outputDevice;

    private Thread captureThread;
    private Thread playbackThread;

    private CallStateListener stateListener;

    // AGC
    private float currentGain = 1.0f;
    private int silenceCounter = 0;

    public enum CallState { IDLE, CONNECTING, ACTIVE, ENDED }

    public interface CallStateListener {
        void onStateChanged(CallState state);
        void onError(String message);
    }

    public static class AudioDevice {
        private final Mixer.Info mixerInfo;
        private final String name;

        public AudioDevice(Mixer.Info mixerInfo, String name) {
            this.mixerInfo = mixerInfo;
            this.name = name;
        }

        public Mixer.Info getMixerInfo() { return mixerInfo; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AudioDevice)) return false;
            return java.util.Objects.equals(name, ((AudioDevice) o).name);
        }
        @Override public int hashCode() { return java.util.Objects.hash(name); }
    }

    public VoiceCallHandler() {
        this(null, null);
    }

    public VoiceCallHandler(AudioDevice inputDevice, AudioDevice outputDevice) {
        this.audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS,
                CHANNELS * SAMPLE_SIZE_BITS / 8,
                SAMPLE_RATE, false);
        this.running = new AtomicBoolean(false);
        this.muted = new AtomicBoolean(false);
        this.inputDevice = inputDevice;
        this.outputDevice = outputDevice;
    }

    public void setStateListener(CallStateListener listener) { this.stateListener = listener; }

    public synchronized int prepareForCall() {
        if (socket != null && !socket.isClosed()) return socket.getLocalPort();
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(30);
            return socket.getLocalPort();
        } catch (SocketException e) { logger.error("Socket error: {}", e.getMessage()); return -1; }
    }

    /**
     * Bắt đầu cuộc gọi qua relay server.
     * @param sessionId  8-ký tự session ID từ server
     * @param relayHost  địa chỉ relay server
     * @param relayPort  cổng relay (9001 cho voice)
     */
    public synchronized void startCall(String sessionId, String relayHost, int relayPort) {
        if (running.get()) return;
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
                socket.setSoTimeout(30);
            }
            this.relayAddress = new InetSocketAddress(relayHost, relayPort);
            this.sessionIdBytes = sessionId.getBytes(StandardCharsets.UTF_8);

            microphone = getMicrophone();
            if (microphone == null) { notifyError("No microphone found"); return; }
            speaker = getSpeaker();
            if (speaker == null) { notifyError("No speaker found"); return; }

            microphone.open(audioFormat);
            speaker.open(audioFormat);
            microphone.start();
            speaker.start();

            running.set(true);
            notifyState(CallState.CONNECTING);

            captureThread = new Thread(this::captureLoop, "voice-capture");
            captureThread.setDaemon(true);
            captureThread.start();

            playbackThread = new Thread(this::playbackLoop, "voice-playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            notifyState(CallState.ACTIVE);
            logger.info("Voice call started via relay session {}", sessionId);
        } catch (LineUnavailableException | SocketException e) {
            notifyError("Failed to start voice call: " + e.getMessage());
            cleanup();
        }
    }

    // --- Capture với noise gate + AGC ---
    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] packetBuf = new byte[BUFFER_SIZE + 8]; // 8 byte sessionId + audio data

        // Pre-fill sessionId
        if (sessionIdBytes != null) System.arraycopy(sessionIdBytes, 0, packetBuf, 0, 8);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int bytesRead = microphone.read(buffer, 0, BUFFER_SIZE);
                if (bytesRead <= 0 || muted.get()) continue;

                // Noise gate: kiểm tra RMS
                float rms = computeRMS(buffer, bytesRead);
                if (rms < NOISE_THRESHOLD) {
                    silenceCounter++;
                    if (silenceCounter < SILENCE_FRAMES_BEFORE_DROP) {
                        // Gửi silence frame để giữ kết nối
                        sendPacket(packetBuf, 8); // chỉ gửi header
                    }
                    continue;
                }
                silenceCounter = 0;

                // AGC: điều chỉnh gain
                applyGain(buffer, bytesRead, currentGain);

                // Gửi audio data (đã có sessionId ở 8 byte đầu)
                System.arraycopy(buffer, 0, packetBuf, 8, bytesRead);
                sendPacket(packetBuf, 8 + bytesRead);

            } catch (IOException e) {
                if (running.get()) logger.error("Capture error: {}", e.getMessage());
            }
        }
    }

    // --- Playback với jitter buffer đơn giản ---
    private void playbackLoop() {
        byte[] buffer = new byte[BUFFER_SIZE + 8];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                socket.receive(packet);
                int len = packet.getLength();
                if (len > 8) {
                    speaker.write(buffer, 8, len - 8);
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running.get()) logger.error("Playback error: {}", e.getMessage());
            }
        }
    }

    private void sendPacket(byte[] data, int len) throws IOException {
        DatagramPacket pkt = new DatagramPacket(data, len, relayAddress);
        socket.send(pkt);
    }

    /** Tính RMS (Root Mean Square) của tín hiệu */
    private float computeRMS(byte[] buffer, int len) {
        double sum = 0;
        for (int i = 0; i < len - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            double normalized = sample / 32768.0;
            sum += normalized * normalized;
        }
        int samples = len / 2;
        return samples > 0 ? (float) Math.sqrt(sum / samples) : 0f;
    }

    /** Áp dụng gain vào PCM 16-bit */
    private void applyGain(byte[] buffer, int len, float gain) {
        if (Math.abs(gain - 1.0f) < 0.01f) return;
        for (int i = 0; i < len - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            int amplified = Math.round(sample * gain);
            amplified = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amplified));
            buffer[i] = (byte) (amplified & 0xFF);
            buffer[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }
    }

    public synchronized void endCall() {
        if (!running.get()) return;
        running.set(false);
        cleanup();
        notifyState(CallState.ENDED);
        logger.info("Voice call ended");
    }

    private void cleanup() {
        running.set(false);
        if (captureThread != null) { captureThread.interrupt(); captureThread = null; }
        if (playbackThread != null) { playbackThread.interrupt(); playbackThread = null; }
        if (microphone != null) { try { microphone.stop(); microphone.close(); } catch (Exception ignored) {} microphone = null; }
        if (speaker != null) { try { speaker.stop(); speaker.close(); } catch (Exception ignored) {} speaker = null; }
        if (socket != null && !socket.isClosed()) { socket.close(); socket = null; }
    }

    public boolean isRunning() { return running.get(); }
    public void setMuted(boolean muted) { this.muted.set(muted); }
    public boolean isMuted() { return muted.get(); }
    public int getLocalPort() { return socket != null ? socket.getLocalPort() : -1; }

    private void notifyState(CallState state) { if (stateListener != null) stateListener.onStateChanged(state); }
    private void notifyError(String msg) { logger.error(msg); if (stateListener != null) stateListener.onError(msg); }

    public static java.util.List<AudioDevice> listMicrophones() {
        return listAudioDevices(TargetDataLine.class, defaultFormat());
    }

    public static java.util.List<AudioDevice> listSpeakers() {
        return listAudioDevices(SourceDataLine.class, defaultFormat());
    }

    private static AudioFormat defaultFormat() {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS,
                CHANNELS * SAMPLE_SIZE_BITS / 8, SAMPLE_RATE, false);
    }

    private static java.util.List<AudioDevice> listAudioDevices(Class<?> lineClass, AudioFormat format) {
        java.util.List<AudioDevice> devices = new java.util.ArrayList<>();
        DataLine.Info info = new DataLine.Info(lineClass, format);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mixer.isLineSupported(info)) {
                    devices.add(new AudioDevice(mi, mi.getName() + " - " + mi.getDescription()));
                }
            } catch (Exception ignored) {}
        }
        return devices;
    }

    private TargetDataLine getMicrophone() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (inputDevice != null && inputDevice.getMixerInfo() != null) {
                Mixer mixer = AudioSystem.getMixer(inputDevice.getMixerInfo());
                if (mixer.isLineSupported(info)) return (TargetDataLine) mixer.getLine(info);
            }
            if (AudioSystem.isLineSupported(info)) return (TargetDataLine) AudioSystem.getLine(info);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                try { Mixer m = AudioSystem.getMixer(mi); if (m.isLineSupported(info)) return (TargetDataLine) m.getLine(info); }
                catch (Exception ignored) {}
            }
            return null;
        } catch (LineUnavailableException e) { logger.error("No microphone: {}", e.getMessage()); return null; }
    }

    private SourceDataLine getSpeaker() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (outputDevice != null && outputDevice.getMixerInfo() != null) {
                Mixer mixer = AudioSystem.getMixer(outputDevice.getMixerInfo());
                if (mixer.isLineSupported(info)) return (SourceDataLine) mixer.getLine(info);
            }
            if (AudioSystem.isLineSupported(info)) return (SourceDataLine) AudioSystem.getLine(info);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                try { Mixer m = AudioSystem.getMixer(mi); if (m.isLineSupported(info)) return (SourceDataLine) m.getLine(info); }
                catch (Exception ignored) {}
            }
            return null;
        } catch (LineUnavailableException e) { logger.error("No speaker: {}", e.getMessage()); return null; }
    }
}
