package com.messenger.server;

import com.messenger.server.db.DatabaseManager;
import com.messenger.shared.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatServer {

    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    public static final int WS_PORT = 9080; // WebSocket port for ngrok tunnel

    private final int port;
    private ServerSocket serverSocket;
    private WsChatServer wsServer;
    private final ExecutorService threadPool;
    private final AtomicBoolean running;
    private final Map<Long, ClientHandler> onlineClients; // null value = WebSocket client
    private MediaRelayServer mediaRelay;

    public ChatServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("client-handler-" + t.getId());
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.onlineClients = new HashMap<>();
    }

    public void start() {
        if (running.get()) {
            logger.warn("Server is already running");
            return;
        }

        logger.info("=============================================");
        logger.info("  Messenger Chat Server starting...");
        logger.info("  TCP Port: {}", port);
        logger.info("  WS  Port: {}", WS_PORT);
        logger.info("=============================================");

        try {
            DatabaseManager.configure("127.0.0.1", 3306, "gui_chat", "root", "");
            DatabaseManager.initialize();
            DatabaseManager.initializeSchema();
            logger.info("Database initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database: {}", e.getMessage(), e);
            return;
        }

        // --- Start Media Relay (voice/video NAT traversal) ---
        try {
            mediaRelay = new MediaRelayServer();
            mediaRelay.start();
            logger.info("MediaRelayServer started");
        } catch (Exception e) {
            logger.error("Failed to start MediaRelay: {}", e.getMessage());
        }

        // --- Start WebSocket server for ngrok tunnel ---
        try {
            wsServer = new WsChatServer(new InetSocketAddress(WS_PORT), this);
            wsServer.start();
            logger.info("WebSocket server started on port {}", WS_PORT);
        } catch (Exception e) {
            logger.error("Failed to start WebSocket server: {}", e.getMessage());
            // Non-fatal; TCP server still runs
        }

        // --- Start TCP server ---
        try {
            serverSocket = new ServerSocket(port);
            running.set(true);
            logger.info("TCP server listening on port {}", port);
        } catch (IOException e) {
            logger.error("Failed to start server socket: {}", e.getMessage(), e);
            return;
        }

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("New TCP connection from {}", clientSocket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error accepting connection: {}", e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        logger.info("Shutting down server...");
        running.set(false);

        // Stop media relay
        if (mediaRelay != null) {
            try { mediaRelay.stop(); } catch (Exception e) { logger.warn("Relay stop error: {}", e.getMessage()); }
        }

        // Stop WebSocket server
        if (wsServer != null) {
            try { wsServer.stop(1000); } catch (Exception e) { logger.warn("WS stop error: {}", e.getMessage()); }
        }

        // Disconnect all TCP clients
        synchronized (onlineClients) {
            for (ClientHandler handler : onlineClients.values()) {
                if (handler != null) handler.disconnect();
            }
            onlineClients.clear();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }
        threadPool.shutdownNow();
        DatabaseManager.shutdown();
        logger.info("Server shut down complete");
    }

    public void registerClient(long userId, ClientHandler handler) {
        synchronized (onlineClients) {
            onlineClients.put(userId, handler); // null handler = WebSocket client
        }
        logger.info("User {} is now online.", userId);
    }

    public void removeClient(long userId) {
        synchronized (onlineClients) {
            onlineClients.remove(userId);
        }
        logger.info("User {} went offline.", userId);
    }

    public ClientHandler getClientHandler(long userId) {
        synchronized (onlineClients) {
            return onlineClients.get(userId);
        }
    }

    public boolean isUserOnline(long userId) {
        synchronized (onlineClients) {
            return onlineClients.containsKey(userId);
        }
    }

    public Map<Long, ClientHandler> getOnlineClients() {
        synchronized (onlineClients) {
            return new HashMap<>(onlineClients);
        }
    }

    public MediaRelayServer getMediaRelay() { return mediaRelay; }

    public static void main(String[] args) {
        int port = Protocol.TCP_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }
        ChatServer server = new ChatServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received...");
            server.shutdown();
        }));
        server.start();
    }
}