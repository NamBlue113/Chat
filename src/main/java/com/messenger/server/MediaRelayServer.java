package com.messenger.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Media Relay Server — hoạt động như TURN server đơn giản.
 * Hai client gửi UDP đến relay, relay forward packet cho bên kia.
 *
 * Ports:
 * - 9001: Voice relay
 * - 9002: Video relay
 *
 * Mỗi call session có 2 "slot" (A và B).
 * Khi cả hai đã đăng ký, relay bắt đầu forward.
 */
public class MediaRelayServer {

    private static final Logger logger = LoggerFactory.getLogger(MediaRelayServer.class);

    public static final int VOICE_RELAY_PORT = 9001;
    public static final int VIDEO_RELAY_PORT = 9002;

    // --- Voice relay ---
    private final DatagramSocket voiceSocket;
    private final Thread voiceThread;
    private final AtomicBoolean voiceRunning = new AtomicBoolean(false);
    private final Map<String, RelaySession> voiceSessions = new ConcurrentHashMap<>();

    // --- Video relay ---
    private final DatagramSocket videoSocket;
    private final Thread videoThread;
    private final AtomicBoolean videoRunning = new AtomicBoolean(false);
    private final Map<String, RelaySession> videoSessions = new ConcurrentHashMap<>();

    /**
     * Một phiên relay — 2 endpoint.
     */
    public static class RelaySession {
        public final String sessionId;
        public InetSocketAddress endpointA;
        public InetSocketAddress endpointB;
        public long lastPacketA;
        public long lastPacketB;
        public volatile boolean active = true;

        public RelaySession(String sessionId) {
            this.sessionId = sessionId;
        }

        public boolean isReady() {
            return endpointA != null && endpointB != null && active;
        }

        public InetSocketAddress getOther(InetSocketAddress sender) {
            if (sender.equals(endpointA)) return endpointB;
            if (sender.equals(endpointB)) return endpointA;
            return null;
        }
    }

    public MediaRelayServer() throws SocketException {
        this.voiceSocket = new DatagramSocket(VOICE_RELAY_PORT);
        this.videoSocket = new DatagramSocket(VIDEO_RELAY_PORT);

        voiceSocket.setReceiveBufferSize(256 * 1024);
        videoSocket.setReceiveBufferSize(512 * 1024);

        logger.info("MediaRelay voice socket on port {}", VOICE_RELAY_PORT);
        logger.info("MediaRelay video socket on port {}", VIDEO_RELAY_PORT);

        this.voiceThread = new Thread(this::voiceRelayLoop, "relay-voice");
        this.videoThread = new Thread(this::videoRelayLoop, "relay-video");
    }

    public void start() {
        voiceRunning.set(true);
        videoRunning.set(true);
        voiceThread.setDaemon(true);
        videoThread.setDaemon(true);
        voiceThread.start();
        videoThread.start();
        logger.info("MediaRelayServer started (voice:{}, video:{})", VOICE_RELAY_PORT, VIDEO_RELAY_PORT);
    }

    public void stop() {
        voiceRunning.set(false);
        videoRunning.set(false);
        voiceSocket.close();
        videoSocket.close();
        voiceSessions.clear();
        videoSessions.clear();
        logger.info("MediaRelayServer stopped");
    }

    /** Đăng ký endpoint cho một phiên gọi */
    public void register(String sessionId, InetSocketAddress address, boolean isVideo) {
        Map<String, RelaySession> map = isVideo ? videoSessions : voiceSessions;
        RelaySession session = map.computeIfAbsent(sessionId, RelaySession::new);
        if (session.endpointA == null) {
            session.endpointA = address;
            logger.debug("{} relay session {} → endpoint A: {}", isVideo ? "Video" : "Voice", sessionId, address);
        } else if (session.endpointB == null) {
            session.endpointB = address;
            logger.debug("{} relay session {} → endpoint B: {}", isVideo ? "Video" : "Voice", sessionId, address);
        }
    }

    /** Hủy đăng ký — xoá khỏi cả voice và video */
    public void unregister(String sessionId) {
        voiceSessions.remove(sessionId);
        videoSessions.remove(sessionId);
        logger.debug("Relay session {} removed", sessionId);
    }

    /** Hủy đăng ký — chỉ voice hoặc video */
    public void unregister(String sessionId, boolean isVideo) {
        Map<String, RelaySession> map = isVideo ? videoSessions : voiceSessions;
        map.remove(sessionId);
        logger.debug("{} relay session {} removed", isVideo ? "Video" : "Voice", sessionId);
    }

    // --- Voice relay loop ---
    private void voiceRelayLoop() {
        byte[] buf = new byte[2048];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        logger.info("Voice relay listening on port {}", VOICE_RELAY_PORT);

        while (voiceRunning.get()) {
            try {
                voiceSocket.receive(pkt);
                InetSocketAddress sender = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                int len = pkt.getLength();

                // Đọc sessionId từ 8 byte đầu
                if (len < 9) continue;
                String sessionId = new String(buf, 0, 8);
                RelaySession session = voiceSessions.get(sessionId);

                if (session == null) {
                    // Auto-register: gói đầu tiên từ một endpoint sẽ đăng ký
                    continue;
                }

                if (!session.isReady()) {
                    // Đăng ký endpoint nếu chưa có
                    if (session.endpointA == null) {
                        session.endpointA = sender;
                    } else if (session.endpointB == null && !sender.equals(session.endpointA)) {
                        session.endpointB = sender;
                    }
                    continue;
                }

                // Forward
                InetSocketAddress target = session.getOther(sender);
                if (target != null) {
                    // Bỏ 8 byte sessionId header, forward phần còn lại
                    DatagramPacket fwd = new DatagramPacket(buf, 8, len - 8, target);
                    voiceSocket.send(fwd);
                }
            } catch (IOException e) {
                if (voiceRunning.get()) logger.error("Voice relay error: {}", e.getMessage());
            }
        }
    }

    // --- Video relay loop ---
    private void videoRelayLoop() {
        byte[] buf = new byte[65536];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        logger.info("Video relay listening on port {}", VIDEO_RELAY_PORT);

        while (videoRunning.get()) {
            try {
                videoSocket.receive(pkt);
                InetSocketAddress sender = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                int len = pkt.getLength();

                if (len < 9) continue;
                String sessionId = new String(buf, 0, 8);
                RelaySession session = videoSessions.get(sessionId);

                if (session == null) continue;

                if (!session.isReady()) {
                    if (session.endpointA == null) {
                        session.endpointA = sender;
                    } else if (session.endpointB == null && !sender.equals(session.endpointA)) {
                        session.endpointB = sender;
                    }
                    continue;
                }

                InetSocketAddress target = session.getOther(sender);
                if (target != null) {
                    DatagramPacket fwd = new DatagramPacket(buf, 8, len - 8, target);
                    videoSocket.send(fwd);
                }
            } catch (IOException e) {
                if (videoRunning.get()) logger.error("Video relay error: {}", e.getMessage());
            }
        }
    }

}

