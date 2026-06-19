package com.messenger.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerConnection {

    private static final Logger logger = LoggerFactory.getLogger(ServerConnection.class);
    private static final Gson gson = new Gson();

    private final String host;
    private final int port;
    private volatile Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private Thread readerThread;
    private final Object closeLock = new Object();
    private ServerListener listener;

    public interface ServerListener {
        void onConnected();
        void onDisconnected();
        void onMessage(JsonObject message);
        void onError(String error);
    }

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setListener(ServerListener listener) { this.listener = listener; }

    public void connect() {
        if (connected.get()) return;
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(0);
            socket.setTcpNoDelay(true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            connected.set(true);
            logger.info("Connected to server {}:{}", host, port);
            if (listener != null) listener.onConnected();
            readerThread = new Thread(this::readLoop, "server-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            logger.error("Failed to connect: {}", e.getMessage());
            if (listener != null) listener.onError("Cannot connect: " + e.getMessage());
        }
    }

    private void readLoop() {
        try {
            String line;
            while (connected.get() && (line = reader.readLine()) != null) {
                if (Thread.interrupted()) break;  // honour interrupt
                if (line.trim().isEmpty()) continue;
                try {
                    JsonObject message = gson.fromJson(line, JsonObject.class);
                    if (listener != null) listener.onMessage(message);
                } catch (Exception e) {
                    logger.warn("Failed to parse: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (connected.get()) logger.error("Connection lost: {}", e.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    private void handleDisconnect() {
        boolean wasConnected = connected.getAndSet(false);
        close();
        if (wasConnected && listener != null) listener.onDisconnected();
    }

    public void send(JsonObject message) {
        if (!connected.get() || writer == null) {
            if (listener != null) {
                Platform.runLater(() -> listener.onError("Not connected to server"));
            }
            return;
        }
        try {
            writer.println(gson.toJson(message));
        } catch (Exception e) {
            logger.error("Failed to send: {}", e.getMessage());
            handleDisconnect();
        }
    }

    public void disconnect() {
        connected.set(false);
        // Interrupt reader thread so readLine() unblocks immediately
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        // Close resources on a background thread to avoid blocking the caller
        new Thread(this::close, "connection-closer").start();
    }

    private void close() {
        synchronized (closeLock) {
            try { if (reader != null) reader.close(); } catch (IOException ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isConnected() { return connected.get() && socket != null && !socket.isClosed(); }
}
