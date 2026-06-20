package com.messenger.server;

import com.messenger.shared.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Media Relay Server — TURN-like relay cho voice (UDP) và video (TCP binary).
 *
 * Ports:
 * - 9001: Voice relay (UDP)
 * - 9004: Video relay (TCP binary, raw JPEG frames)
 *
 * Protocol TCP video:
 *   1. Client kết nối → gửi sessionId (8 bytes ASCII) để đăng ký
 *   2. Client gửi frame: [frameLen: 4 bytes big-endian] [frameData: N bytes]
 *   3. Server forward tới peer: [frameLen: 4 bytes] [frameData: N bytes]
 */
public class MediaRelayServer {

    private static final Logger logger = LoggerFactory.getLogger(MediaRelayServer.class);

    public static final int VOICE_RELAY_PORT = 9001;
    public static final int VIDEO_RELAY_PORT = 9002; // kept for compatibility
    public static final int MEDIA_TCP_PORT   = Protocol.MEDIA_TCP_PORT; // 9004

    // --- Voice relay (UDP) ---
    private final DatagramSocket voiceSocket;
    private final Thread voiceThread;
    private final AtomicBoolean voiceRunning = new AtomicBoolean(false);
    private final Map<String, RelaySession> voiceSessions = new ConcurrentHashMap<>();

    // --- Video relay (TCP binary) ---
    private ServerSocket tcpRelaySocket;
    private Thread tcpAcceptThread;
    private final AtomicBoolean tcpRunning = new AtomicBoolean(false);
    private final Map<String, TcpRelaySession> tcpSessions = new ConcurrentHashMap<>();

    // ========== UDP relay session ==========

    public static class RelaySession {
        public final String sessionId;
        public InetSocketAddress endpointA;
        public InetSocketAddress endpointB;
        public long lastPacketA;
        public long lastPacketB;
        public volatile boolean active = true;

        public RelaySession(String sessionId) { this.sessionId = sessionId; }

        public boolean isReady() { return endpointA != null && endpointB != null && active; }

        public InetSocketAddress getOther(InetSocketAddress sender) {
            if (sender.equals(endpointA)) return endpointB;
            if (sender.equals(endpointB)) return endpointA;
            return null;
        }
    }

    // ========== TCP relay session ==========

    private static class TcpRelaySession {
        final String sessionId;
        volatile DataOutputStream peerA;
        volatile DataOutputStream peerB;

        TcpRelaySession(String sessionId) { this.sessionId = sessionId; }

        synchronized boolean registerPeer(DataOutputStream out) {
            if (peerA == null) { peerA = out; return true; }
            if (peerB == null && out != peerA) { peerB = out; return true; }
            return false;
        }

        synchronized DataOutputStream getOther(DataOutputStream self) {
            if (self == peerA) return peerB;
            if (self == peerB) return peerA;
            return null;
        }

        synchronized void removePeer(DataOutputStream out) {
            if (out == peerA) peerA = null;
            if (out == peerB) peerB = null;
        }
    }

    // ========== Constructor ==========

    public MediaRelayServer() throws SocketException {
        this.voiceSocket = new DatagramSocket(VOICE_RELAY_PORT);
        voiceSocket.setReceiveBufferSize(256 * 1024);
        logger.info("MediaRelay voice socket on port {}", VOICE_RELAY_PORT);

        this.voiceThread = new Thread(this::voiceRelayLoop, "relay-voice");

        try {
            this.tcpRelaySocket = new ServerSocket(MEDIA_TCP_PORT);
            this.tcpAcceptThread = new Thread(this::tcpAcceptLoop, "relay-tcp-accept");
            logger.info("MediaRelay TCP video socket on port {}", MEDIA_TCP_PORT);
        } catch (IOException e) {
            logger.error("Failed to bind TCP relay on port {}: {}", MEDIA_TCP_PORT, e.getMessage());
        }
    }

    // ========== Lifecycle ==========

    public void start() {
        voiceRunning.set(true);
        voiceThread.setDaemon(true);
        voiceThread.start();

        if (tcpRelaySocket != null && !tcpRelaySocket.isClosed()) {
            tcpRunning.set(true);
            tcpAcceptThread.setDaemon(true);
            tcpAcceptThread.start();
        }

        logger.info("MediaRelayServer started (voice UDP:{}, video TCP:{})",
                VOICE_RELAY_PORT, MEDIA_TCP_PORT);
    }

    public void stop() {
        voiceRunning.set(false);
        tcpRunning.set(false);
        voiceSocket.close();
        try { if (tcpRelaySocket != null) tcpRelaySocket.close(); } catch (IOException ignored) {}
        voiceSessions.clear();
        tcpSessions.clear();
        logger.info("MediaRelayServer stopped");
    }

    // ========== UDP relay session management ==========

    public void register(String sessionId, InetSocketAddress address, boolean isVideo) {
        RelaySession session = voiceSessions.computeIfAbsent(sessionId, RelaySession::new);
        if (session.endpointA == null) {
            session.endpointA = address;
        } else if (session.endpointB == null) {
            session.endpointB = address;
        }
    }

    public void unregister(String sessionId) {
        voiceSessions.remove(sessionId);
        tcpSessions.remove(sessionId);
        logger.debug("Relay session {} removed", sessionId);
    }

    public void unregister(String sessionId, boolean isVideo) {
        if (isVideo) tcpSessions.remove(sessionId);
        else voiceSessions.remove(sessionId);
        logger.debug("{} relay session {} removed", isVideo ? "Video" : "Voice", sessionId);
    }

    // ========== UDP voice relay loop ==========

    private void voiceRelayLoop() {
        byte[] buf = new byte[2048];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        logger.info("Voice relay listening on port {}", VOICE_RELAY_PORT);

        while (voiceRunning.get()) {
            try {
                voiceSocket.receive(pkt);
                InetSocketAddress sender = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                int len = pkt.getLength();
                if (len < 9) continue;

                String sessionId = new String(buf, 0, 8);
                RelaySession session = voiceSessions.get(sessionId);
                if (session == null) continue;

                if (!session.isReady()) {
                    if (session.endpointA == null) session.endpointA = sender;
                    else if (session.endpointB == null && !sender.equals(session.endpointA)) session.endpointB = sender;
                    continue;
                }

                InetSocketAddress target = session.getOther(sender);
                if (target != null) {
                    DatagramPacket fwd = new DatagramPacket(buf, 8, len - 8, target);
                    voiceSocket.send(fwd);
                }
            } catch (IOException e) {
                if (voiceRunning.get()) logger.error("Voice relay error: {}", e.getMessage());
            }
        }
    }

    // ========== TCP video relay: accept loop ==========

    private void tcpAcceptLoop() {
        logger.info("TCP video relay listening on port {}", MEDIA_TCP_PORT);
        while (tcpRunning.get()) {
            try {
                Socket client = tcpRelaySocket.accept();
                client.setTcpNoDelay(true);
                Thread t = new Thread(() -> handleTcpPeer(client), "relay-tcp-peer");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (tcpRunning.get()) logger.error("TCP relay accept error: {}", e.getMessage());
            }
        }
    }

    // ========== TCP video relay: per-peer handler ==========

    private void handleTcpPeer(Socket socket) {
        TcpRelaySession session = null;
        DataOutputStream myOut = null;
        try {
            DataInputStream in   = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 32768));
            myOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 32768));

            // Đọc sessionId đăng ký (8 bytes đầu tiên)
            byte[] sidBytes = new byte[8];
            in.readFully(sidBytes);
            String sessionId = new String(sidBytes).trim();

            session = tcpSessions.computeIfAbsent(sessionId, TcpRelaySession::new);
            if (!session.registerPeer(myOut)) {
                logger.warn("TCP relay: session {} already has 2 peers, rejecting", sessionId);
                return;
            }
            logger.debug("TCP video relay: peer joined session {}", sessionId);

            // Vòng lặp relay frame
            final DataOutputStream finalOut = myOut;
            final TcpRelaySession finalSession = session;
            while (!socket.isClosed()) {
                int frameLen = in.readInt();
                if (frameLen <= 0 || frameLen > 500 * 1024) {
                    logger.warn("TCP relay: invalid frameLen={} in session {}", frameLen, sessionId);
                    break;
                }
                byte[] frameData = new byte[frameLen];
                in.readFully(frameData);

                DataOutputStream peer = finalSession.getOther(finalOut);
                if (peer != null) {
                    synchronized (peer) {
                        peer.writeInt(frameLen);
                        peer.write(frameData, 0, frameLen);
                        peer.flush();
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            // peer disconnected — normal
        } catch (IOException e) {
            logger.debug("TCP relay peer error: {}", e.getMessage());
        } finally {
            if (session != null && myOut != null) session.removePeer(myOut);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
