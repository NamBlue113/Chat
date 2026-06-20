package com.messenger.client.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TCP binary client cho video relay (port 9004).
 *
 * Protocol:
 *   Kết nối → gửi sessionId (8 bytes) để đăng ký
 *   Gửi frame: [frameLen: 4 bytes big-endian] [frameData: N bytes]
 *   Nhận frame: [frameLen: 4 bytes big-endian] [frameData: N bytes]
 */
public class TcpVideoClient {

    private static final Logger log = LoggerFactory.getLogger(TcpVideoClient.class);

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private Thread receiveThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Consumer<byte[]> frameListener;

    public void setFrameListener(Consumer<byte[]> listener) {
        this.frameListener = listener;
    }

    /**
     * Kết nối tới relay server và đăng ký session.
     * Gọi trên background thread — KHÔNG gọi trên JavaFX thread.
     */
    public void connect(String host, int port, String sessionId) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(0); // no timeout on receive

        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 32768));
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 32768));

        // Gửi sessionId (8 bytes, right-padded với spaces)
        byte[] sidBytes = new byte[8];
        byte[] raw = sessionId.getBytes();
        System.arraycopy(raw, 0, sidBytes, 0, Math.min(raw.length, 8));
        out.write(sidBytes);
        out.flush();

        running.set(true);
        receiveThread = new Thread(this::receiveLoop, "tcp-video-recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        log.info("TcpVideoClient connected to {}:{} session={}", host, port, sessionId);
    }

    /**
     * Gửi một JPEG frame. Thread-safe.
     */
    public void sendFrame(byte[] jpegBytes) {
        if (!running.get() || out == null) return;
        try {
            synchronized (out) {
                out.writeInt(jpegBytes.length);
                out.write(jpegBytes);
                out.flush();
            }
        } catch (IOException e) {
            if (running.get()) log.warn("sendFrame error: {}", e.getMessage());
            running.set(false);
        }
    }

    /**
     * Vòng lặp nhận frame từ peer (chạy trên background thread).
     */
    private void receiveLoop() {
        try {
            while (running.get()) {
                int frameLen = in.readInt();
                if (frameLen <= 0 || frameLen > 500 * 1024) {
                    log.warn("TcpVideoClient: invalid frameLen={}", frameLen);
                    break;
                }
                byte[] data = new byte[frameLen];
                in.readFully(data);
                if (frameListener != null) {
                    frameListener.accept(data);
                }
            }
        } catch (EOFException | SocketException e) {
            // kết nối đóng — bình thường
        } catch (IOException e) {
            if (running.get()) log.warn("TcpVideoClient receive error: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * Đóng kết nối.
     */
    public void close() {
        running.set(false);
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        log.info("TcpVideoClient closed");
    }

    public boolean isConnected() {
        return running.get() && socket != null && !socket.isClosed();
    }
}
