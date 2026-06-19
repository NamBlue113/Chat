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

import javax.imageio.ImageIO;
import java.awt.Dimension;
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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Webcam webcam;
    private Thread captureThread;
    private ImageView remoteView, localView;
    private ChatClient client;
    private long targetId;
    private boolean isGroup;
    private int seq;
    private Consumer<String> stateListener, errorListener;
    private int selectedCameraIndex = 0;

    public static List<String> listCameras() {
        return Webcam.getWebcams().stream()
                .map(Webcam::getName)
                .collect(Collectors.toList());
    }

    public void selectCamera(int index) {
        this.selectedCameraIndex = index;
    }
    public void setViews(ImageView remote, ImageView local) { remoteView = remote; localView = local; }
    public void setClient(ChatClient c) { client = c; }
    public void setStateListener(Consumer<String> l) { stateListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }

    public void start(long targetId, boolean isGroup) { 
        if (running.get()) return;
        this.targetId = targetId;
        this.isGroup = isGroup;
        this.seq = 0;
        try {
            List<Webcam> cams = Webcam.getWebcams();
            if (cams.isEmpty()) {
                notifyError("Khong tim thay webcam");
                return;
            }
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
        } catch (Exception e) { notifyError("Loi webcam: " + e.getMessage()); cleanup(); }
    }

    private void loop() {
        while (running.get()) {
            long t = System.currentTimeMillis();
            try {
                BufferedImage img = webcam.getImage();
                if (img == null) continue;
                // Local preview
                if (localView != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", bos);
                    byte[] d = bos.toByteArray();
                    Platform.runLater(() -> localView.setImage(new Image(new ByteArrayInputStream(d))));
                }
                // Encode + send
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", bos);
                String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                JsonObject data = new JsonObject();
                if (isGroup) data.addProperty("groupId", targetId);
                else data.addProperty("receiverId", targetId);
                data.addProperty("frame", b64);
                data.addProperty("seq", seq++);
                client.send(Protocol.TYPE_VIDEO_FRAME, data);
                long el = System.currentTimeMillis() - t;
                if (el < INTERVAL) Thread.sleep(INTERVAL - el);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            catch (Exception e) { if (running.get()) log.error("capture: {}", e.getMessage()); }
        }
    }

    public void onFrame(String b64) {
        if (!running.get() || remoteView == null) return;
        try {
            Image img = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
            Platform.runLater(() -> remoteView.setImage(img));
        } catch (Exception e) { log.warn("frame decode: {}", e.getMessage()); }
    }

    public void end() {
        if (!running.get()) return;
        running.set(false);
        cleanup();
        JsonObject d = new JsonObject();
        if (isGroup) d.addProperty("groupId", targetId);
        else d.addProperty("receiverId", targetId);
        client.send(Protocol.TYPE_VIDEO_CALL_END, d);
        notify("ended");
    }

    public boolean isRunning() { return running.get(); }

    private void cleanup() {
        if (captureThread != null) { captureThread.interrupt(); captureThread = null; }
        if (webcam != null && webcam.isOpen()) { try { webcam.close(); } catch (Exception ignored) {} webcam = null; }
        Platform.runLater(() -> { if (remoteView != null) remoteView.setImage(null); if (localView != null) localView.setImage(null); });
    }

    private void notify(String s) { if (stateListener != null) stateListener.accept(s); }
    private void notifyError(String m) { log.error(m); if (errorListener != null) errorListener.accept(m); }
}
