package com.messenger.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.messenger.client.config.AppConfig;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket realtime client for chat messaging.
 * Uses the same JSON protocol as the legacy TCP transport.
 */
public class RealtimeClient {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeClient.class);
    private static final Gson gson = new Gson();

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean manuallyClosed = new AtomicBoolean(false);
    private final StringBuilder messageBuffer = new StringBuilder();
    private Listener listener;

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onMessage(JsonObject message);
        void onError(String error);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void connect() {
        if (connected.get()) return;
        manuallyClosed.set(false);
        connectTo(AppConfig.wsUri(), AppConfig.isNgrok());
    }

    private void connectTo(URI uri, boolean allowLocalFallback) {
        logger.info("Connecting WebSocket to {}", uri);

        CompletableFuture<WebSocket> future = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        webSocket = ws;
                        connected.set(true);
                        logger.info("WebSocket connected to {}", uri);
                        WebSocket.Listener.super.onOpen(ws);
                        notifyConnected();
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        if (last) {
                            String line = messageBuffer.toString().trim();
                            messageBuffer.setLength(0);
                            if (!line.isEmpty()) {
                                try {
                                    JsonObject msg = gson.fromJson(line, JsonObject.class);
                                    notifyMessage(msg);
                                } catch (Exception e) {
                                    logger.warn("Failed to parse WS message: {}", e.getMessage());
                                }
                            }
                        }
                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        connected.set(false);
                        logger.info("WebSocket closed: {} {}", statusCode, reason);
                        WebSocket.Listener.super.onClose(ws, statusCode, reason);
                        if (!manuallyClosed.get()) notifyDisconnected();
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        connected.set(false);
                        String msg = error.getMessage() != null ? error.getMessage() : "Unknown WebSocket error";
                        logger.error("WebSocket error: {}", msg);
                        if (!manuallyClosed.get()) notifyError("Mat ket noi may chu: " + msg);
                        WebSocket.Listener.super.onError(ws, error);
                    }
                });

        future.exceptionally(throwable -> {
            connected.set(false);
            String msg = throwable.getMessage() != null ? throwable.getMessage() : "Connection failed";
            logger.error("WebSocket connect failed: {}", msg);

            if (allowLocalFallback && !manuallyClosed.get()) {
                URI localUri = URI.create("ws://127.0.0.1:9080" + normalizedWsPath());
                logger.warn("Ngrok WebSocket failed, trying local fallback {}", localUri);
                connectTo(localUri, false);
                return null;
            }

            if (!manuallyClosed.get()) {
                notifyError("Khong the ket noi may chu. Kiem tra ChatServer port 9080, ngrok http 9080, va link trong app.properties. Chi tiet: " + msg);
            }
            return null;
        });

        future.thenAccept(ws -> ws.request(1));
    }

    public void send(JsonObject message) {
        if (!connected.get() || webSocket == null) {
            notifyError("Chua ket noi toi may chu");
            return;
        }
        try {
            String json = gson.toJson(message);
            logger.debug("WS send: {}", json);
            webSocket.sendText(json, true).exceptionally(t -> {
                logger.error("WS send failed: {}", t.getMessage());
                notifyError("Gui du lieu that bai: " + t.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.error("Failed to send WS message: {}", e.getMessage());
            notifyError("Gui du lieu that bai: " + e.getMessage());
        }
    }

    public void disconnect() {
        manuallyClosed.set(true);
        connected.set(false);
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isConnected() {
        return connected.get() && webSocket != null && !webSocket.isOutputClosed();
    }

    private String normalizedWsPath() {
        String path = AppConfig.getWsPath();
        if (path == null || path.isBlank()) return "/ws";
        return path.startsWith("/") ? path : "/" + path;
    }

    private void notifyConnected() {
        if (listener != null) Platform.runLater(() -> listener.onConnected());
    }

    private void notifyDisconnected() {
        if (listener != null) Platform.runLater(() -> listener.onDisconnected());
    }

    private void notifyMessage(JsonObject message) {
        if (listener != null) Platform.runLater(() -> listener.onMessage(message));
    }

    private void notifyError(String error) {
        if (listener != null) Platform.runLater(() -> listener.onError(error));
    }
}
