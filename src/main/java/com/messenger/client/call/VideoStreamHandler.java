package com.messenger.client.call;

import com.github.sarxos.webcam.Webcam;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import com.messenger.client.ChatClient;
import com.messenger.shared.Protocol;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoStreamHandler.class);
    private static final Dimension SIZE = new Dimension(320, 240);
    private static final int FPS = 15;
    private static final int INTERVAL = 1000 / FPS;
    private static final float JPEG_QUALITY = 0.6f;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean frameRendering = new AtomicBoolean(false);
    private Webcam webcam;
    private Thread captureThread;
    private ImageView remoteView, localView;
    private ChatClient client;       // dùng cho signaling (CALL_END)
    private TcpVideoClient tcpClient; // dùng cho video frames
    private long targetId;
    private boolean isGroup;
    private int seq;
    private Consumer<String> stateListener, errorListener;
    private int selectedCameraIndex = 0;

    // ========== Static helpers ==========

    public static List<String> listCameras() {
        return Webcam.getWebcams().stream()
                .map(Webcam::getName)
                .collect(Collectors.toList());
    }

    // ========== Configuration ==========

    public void selectCamera(int index) { this.selectedCameraIndex = index; }
    public void setViews(ImageView remote, ImageView local) { remoteView = remote; localView = local; }
    public void setClient(ChatClient c) { client = c; }
    public void setTcpClient(TcpVideoClient tcp) { this.tcpClient = tcp; }
    public void setStateListener(Consumer<String> l) { stateListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }

    // ========== Lifecycle ==========

    /**
     * Khởi động capture trên background thread (tránh block JavaFX thread).
     * TcpVideoClient phải được set trước hoặc sau (sẽ kiểm tra trong loop).
     */
    public void start(long targetId, boolean isGroup) {
        if (running.get()) return;
        this.targetId = targetId;
        this.isGroup = isGroup;
        this.seq = 0;

        Thread initThread = new Thread(() -> {
            try {
                List<Webcam> cams = Webcam.getWebcams();
                if (cams.isEmpty()) { notifyError("Khong tim thay webcam"); return; }
                webcam = cams.get(Math.min(selectedCameraIndex, cams.size() - 1));
                if (webcam == null) { notifyError("Khong tim thay webcam"); return; }

                Dimension best = SIZE;
                for (Dimension s : webcam.getViewSizes())
                    if (s.width <= SIZE.width && s.height <= SIZE.height) best = s;
                webcam.setViewSize(best);
                webcam.open();
                if (!webcam.isOpen()) { notifyError("Khong the mo webcam"); return; }

                running.set(true);
                captureThread = new Thread(this::loop, "video-capture");
                captureThread.setDaemon(true);
                captureThread.start();
                notify("connected");
            } catch (Exception e) {
                notifyError("Loi webcam: " + e.getMessage());
                cleanup();
            }
        }, "video-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * Dừng capture và gửi tín hiệu kết thúc tới peer.
     * Dùng khi người dùng chủ động kết thúc cuộc gọi.
     */
    public void end() {
        if (!running.get()) return;
        running.set(false);
        cleanup();
        if (client != null) {
            JsonObject d = new JsonObject();
            if (isGroup) d.addProperty("groupId", targetId);
            else d.addProperty("receiverId", targetId);
            client.send(Protocol.TYPE_VIDEO_CALL_END, d);
        }
        notify("ended");
    }

    /**
     * Dừng capture mà KHÔNG gửi tín hiệu kết thúc.
     * Dùng khi đổi camera/thiết bị trong Settings — cuộc gọi vẫn tiếp tục.
     */
    public void stopCapture() {
        if (!running.get()) return;
        running.set(false);
        cleanup();
    }

    public boolean isRunning() { return running.get(); }

    // ========== Capture loop ==========

    private void loop() {
        while (running.get()) {
            long t = System.currentTimeMillis();
            try {
                BufferedImage raw = webcam.getImage();
                if (raw == null) continue;

                // Chuyển sang TYPE_INT_RGB — JPEG không hỗ trợ alpha (ARGB)
                // Đây là bước quan trọng, thiếu sẽ ra màn hình đen
                BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.drawImage(raw, 0, 0, null);
                g.dispose();

                // Encode JPEG một lần, dùng cho cả local preview và gửi đi
                ByteArrayOutputStream bos = new ByteArrayOutputStream(32768);
                writeJpeg(img, bos, JPEG_QUALITY);
                byte[] frameBytes = bos.toByteArray();

                // Local preview (JavaFX thread)
                if (localView != null) {
                    byte[] preview = frameBytes;
                    Platform.runLater(() -> localView.setImage(new Image(new ByteArrayInputStream(preview))));
                }

                // Gửi frame qua TCP binary (ưu tiên) hoặc fallback WebSocket
                if (tcpClient != null && tcpClient.isConnected()) {
                    tcpClient.sendFrame(frameBytes);
                } else if (client != null) {
                    // Fallback: WebSocket base64 (backup khi TCP chưa kết nối)
                    String b64 = Base64.getEncoder().encodeToString(frameBytes);
                    JsonObject data = new JsonObject();
                    if (isGroup) data.addProperty("groupId", targetId);
                    else data.addProperty("receiverId", targetId);
                    data.addProperty("frame", b64);
                    data.addProperty("seq", seq++);
                    client.send(Protocol.TYPE_VIDEO_FRAME, data);
                }

                long el = System.currentTimeMillis() - t;
                if (el < INTERVAL) Thread.sleep(INTERVAL - el);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) log.error("capture: {}", e.getMessage());
            }
        }
    }

    // ========== Nhận frame ==========

    /** Nhận JPEG bytes từ TcpVideoClient (binary TCP path). */
    public void onFrameBytes(byte[] jpegBytes) {
        if (remoteView == null) return; // Hiển thị remote dù webcam local chưa mở xong
        if (!frameRendering.compareAndSet(false, true)) return; // drop nếu đang render
        try {
            Image img = new Image(new ByteArrayInputStream(jpegBytes));
            Platform.runLater(() -> {
                remoteView.setImage(img);
                frameRendering.set(false);
            });
        } catch (Exception e) {
            frameRendering.set(false);
            log.warn("frame decode: {}", e.getMessage());
        }
    }

    /** Nhận base64 frame từ WebSocket (fallback path). */
    public void onFrame(String b64) {
        if (!running.get() || remoteView == null) return;
        if (!frameRendering.compareAndSet(false, true)) return;
        try {
            Image img = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
            Platform.runLater(() -> {
                remoteView.setImage(img);
                frameRendering.set(false);
            });
        } catch (Exception e) {
            frameRendering.set(false);
            log.warn("frame decode (ws): {}", e.getMessage());
        }
    }

    // ========== Internal helpers ==========

    private static void writeJpeg(BufferedImage img, ByteArrayOutputStream bos, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void cleanup() {
        if (captureThread != null) { captureThread.interrupt(); captureThread = null; }
        if (webcam != null && webcam.isOpen()) {
            try { webcam.close(); } catch (Exception ignored) {}
            webcam = null;
        }
        Platform.runLater(() -> {
            if (remoteView != null) remoteView.setImage(null);
            if (localView != null) localView.setImage(null);
        });
    }

    private void notify(String s) { if (stateListener != null) stateListener.accept(s); }
    private void notifyError(String m) { log.error(m); if (errorListener != null) errorListener.accept(m); }
}
