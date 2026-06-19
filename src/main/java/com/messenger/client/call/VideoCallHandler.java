package com.messenger.client.call;

import com.github.sarxos.webcam.Webcam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Video call handler với relay support (vượt NAT).
 *
 * Cải tiến:
 * - Qua relay server thay vì UDP trực tiếp
 * - JPEG quality thích ứng (adaptive)
 * - Frame dropping khi bandwidth yếu
 * - Magic header "FRM1" giữ nguyên để backward compat
 */
public class VideoCallHandler {

    private static final Logger logger = LoggerFactory.getLogger(VideoCallHandler.class);

    private static final int FRAME_RATE = 15;
    private static final int MAX_PACKET_SIZE = 65000;
    private static final java.awt.Dimension DEFAULT_VIEW_SIZE = new java.awt.Dimension(320, 240);

    // Adaptive quality
    private static final float QUALITY_HIGH = 0.55f;
    private static final float QUALITY_LOW = 0.25f;
    private static final int HIGH_QUALITY_THRESHOLD_KB = 30;   // below this: use high quality
    private static final int LOW_QUALITY_THRESHOLD_KB = 60;    // above this: drop frames

    private final AtomicBoolean running;
    private final AtomicBoolean videoEnabled;

    private DatagramSocket socket;
    private InetSocketAddress relayAddress;
    private byte[] sessionIdBytes;

    private Webcam webcam;
    private final VideoDevice videoDevice;

    private Thread captureThread;
    private Thread receiveThread;

    private VideoFrameListener frameListener;
    private VideoFrameListener localFrameListener;
    private CallStateListener stateListener;

    private float currentQuality = QUALITY_HIGH;
    private int consecutiveDroppedFrames = 0;

    public interface VideoFrameListener {
        void onFrameReceived(byte[] jpegData, int length);
    }

    public enum CallState { IDLE, CONNECTING, ACTIVE, ENDED }

    public interface CallStateListener {
        void onStateChanged(CallState state);
        void onError(String message);
    }

    public static class VideoDevice {
        private final Webcam webcam;
        private final String name;

        public VideoDevice(Webcam webcam) {
            this.webcam = webcam;
            this.name = webcam != null ? webcam.getName() : "Default camera";
        }

        public Webcam getWebcam() { return webcam; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VideoDevice)) return false;
            return java.util.Objects.equals(name, ((VideoDevice) o).name);
        }
        @Override public int hashCode() { return java.util.Objects.hash(name); }
    }

    public VideoCallHandler() { this(null); }

    public VideoCallHandler(VideoDevice videoDevice) {
        this.running = new AtomicBoolean(false);
        this.videoEnabled = new AtomicBoolean(true);
        this.videoDevice = videoDevice;
    }

    public void setFrameListener(VideoFrameListener listener) { this.frameListener = listener; }
    public void setLocalFrameListener(VideoFrameListener listener) { this.localFrameListener = listener; }
    public void setStateListener(CallStateListener listener) { this.stateListener = listener; }

    public static java.util.List<VideoDevice> listVideoDevices() {
        java.util.List<VideoDevice> devices = new java.util.ArrayList<>();
        for (Webcam cam : Webcam.getWebcams()) devices.add(new VideoDevice(cam));
        return devices;
    }

    public synchronized int prepareForCall() {
        if (socket != null && !socket.isClosed()) return socket.getLocalPort();
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(30);
            return socket.getLocalPort();
        } catch (SocketException e) { logger.error("Socket error: {}", e.getMessage()); return -1; }
    }

    /**
     * Bắt đầu video call qua relay.
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

            webcam = videoDevice != null && videoDevice.getWebcam() != null
                     ? videoDevice.getWebcam() : Webcam.getDefault();
            if (webcam == null) { notifyError("No webcam found"); return; }
            setBestViewSize(webcam);
            webcam.open();
            if (!webcam.isOpen()) { notifyError("Failed to open webcam"); return; }

            running.set(true);
            notifyState(CallState.CONNECTING);

            captureThread = new Thread(this::captureLoop, "video-capture");
            captureThread.setDaemon(true);
            captureThread.start();

            receiveThread = new Thread(this::receiveLoop, "video-receive");
            receiveThread.setDaemon(true);
            receiveThread.start();

            notifyState(CallState.ACTIVE);
            logger.info("Video call started via relay session {}", sessionId);
        } catch (IOException e) { notifyError("Network error: " + e.getMessage()); cleanup(); }
    }

    private void captureLoop() {
        long frameInterval = 1000 / FRAME_RATE;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();
            try {
                if (!videoEnabled.get() || webcam == null || !webcam.isOpen()) {
                    Thread.sleep(frameInterval); continue;
                }
                BufferedImage image = webcam.getImage();
                if (image == null) continue;

                // Local preview
                ByteArrayOutputStream previewBaos = new ByteArrayOutputStream();
                writeJpeg(image, previewBaos, 0.4f);
                byte[] previewData = previewBaos.toByteArray();
                if (localFrameListener != null)
                    localFrameListener.onFrameReceived(previewData, previewData.length);

                // Network send (adaptive quality)
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                writeJpeg(image, baos, currentQuality);
                byte[] jpegData = baos.toByteArray();

                // Adaptive quality adjustment
                if (jpegData.length < HIGH_QUALITY_THRESHOLD_KB * 1024) {
                    currentQuality = Math.min(QUALITY_HIGH, currentQuality + 0.05f);
                } else if (jpegData.length > LOW_QUALITY_THRESHOLD_KB * 1024) {
                    currentQuality = Math.max(QUALITY_LOW, currentQuality - 0.1f);
                    consecutiveDroppedFrames++;
                    if (consecutiveDroppedFrames > 3) {
                        // Skip this frame
                        Thread.sleep(frameInterval);
                        continue;
                    }
                } else {
                    consecutiveDroppedFrames = 0;
                }

                if (jpegData.length > MAX_PACKET_SIZE - 24) {
                    logger.warn("Skipping oversized frame: {} bytes (quality={})", jpegData.length, currentQuality);
                    continue;
                }

                // Build packet: 8-byte sessionId + 8-byte FRM1 header + JPEG data
                int totalLen = 8 + 8 + jpegData.length;
                byte[] pkt = new byte[totalLen];
                System.arraycopy(sessionIdBytes, 0, pkt, 0, 8);
                // FRM1 header
                pkt[8] = 0x46; pkt[9] = 0x52; pkt[10] = 0x4D; pkt[11] = 0x31;
                pkt[12] = (byte) ((jpegData.length >> 8) & 0xFF);
                pkt[13] = (byte) (jpegData.length & 0xFF);
                pkt[14] = 0; pkt[15] = 0;
                System.arraycopy(jpegData, 0, pkt, 16, jpegData.length);

                DatagramPacket packet = new DatagramPacket(pkt, totalLen, relayAddress);
                socket.send(packet);

                long elapsed = System.currentTimeMillis() - startTime;
                long sleep = frameInterval - elapsed;
                if (sleep > 0) Thread.sleep(sleep);
            } catch (IOException e) {
                if (running.get()) logger.error("Capture error: {}", e.getMessage());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE + 32];
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                byte[] data = packet.getData();
                int len = packet.getLength();

                // Skip sessionId (8 bytes), read FRM1 header
                int offset = 8;
                if (len < offset + 6) continue;
                if (data[offset] != 0x46 || data[offset+1] != 0x52 || data[offset+2] != 0x4D) continue;

                if (data[offset+3] == 0x31) { // FRM1 single frame
                    int jpegLength = ((data[offset+4] & 0xFF) << 8) | (data[offset+5] & 0xFF);
                    int jpegStart = offset + 8;
                    if (jpegLength > 0 && len >= jpegStart + jpegLength) {
                        byte[] jpeg = new byte[jpegLength];
                        System.arraycopy(data, jpegStart, jpeg, 0, jpegLength);
                        if (frameListener != null) frameListener.onFrameReceived(jpeg, jpegLength);
                    }
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running.get()) logger.error("Receive error: {}", e.getMessage());
            }
        }
    }

    private void setBestViewSize(Webcam cam) {
        try {
            java.awt.Dimension[] sizes = cam.getViewSizes();
            for (java.awt.Dimension size : sizes) {
                if (size.width == DEFAULT_VIEW_SIZE.width && size.height == DEFAULT_VIEW_SIZE.height) {
                    cam.setViewSize(size); return;
                }
            }
            if (sizes.length > 0) {
                java.awt.Dimension best = sizes[0];
                for (java.awt.Dimension size : sizes) {
                    if (size.width <= DEFAULT_VIEW_SIZE.width && size.height <= DEFAULT_VIEW_SIZE.height) best = size;
                }
                cam.setViewSize(best);
            }
        } catch (Exception e) { logger.warn("Could not set webcam view size: {}", e.getMessage()); }
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream out, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        try (MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally { writer.dispose(); }
    }

    public synchronized void endCall() {
        if (!running.get()) return;
        running.set(false);
        cleanup();
        notifyState(CallState.ENDED);
        logger.info("Video call ended");
    }

    private void cleanup() {
        running.set(false);
        if (captureThread != null) { captureThread.interrupt(); captureThread = null; }
        if (receiveThread != null) { receiveThread.interrupt(); receiveThread = null; }
        if (webcam != null && webcam.isOpen()) { try { webcam.close(); } catch (Exception ignored) {} webcam = null; }
        if (socket != null && !socket.isClosed()) { socket.close(); socket = null; }
    }

    public boolean isRunning() { return running.get(); }
    public void setVideoEnabled(boolean enabled) { this.videoEnabled.set(enabled); }
    public boolean isVideoEnabled() { return videoEnabled.get(); }
    public int getLocalPort() { return socket != null ? socket.getLocalPort() : -1; }

    private void notifyState(CallState state) { if (stateListener != null) stateListener.onStateChanged(state); }
    private void notifyError(String msg) { logger.error(msg); if (stateListener != null) stateListener.onError(msg); }
}
