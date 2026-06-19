package com.messenger.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.messenger.client.config.AppConfig;
import com.messenger.client.net.RealtimeClient;
import com.messenger.client.ui.LoginView;
import com.messenger.client.ui.MainView;
import com.messenger.client.ui.NotificationManager;
import com.messenger.client.ui.RegisterView;
import com.messenger.client.ui.ThemeManager;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatClient extends Application {

    private static ChatClient instance;

    private Stage primaryStage;
    private ServerConnection tcpConnection;   // legacy TCP transport (local dev only)
    private RealtimeClient wsClient;          // WebSocket transport (ngrok / production)
    private boolean useWebSocket;             // true = WebSocket, false = legacy TCP
    private ThemeManager themeManager;
    private NotificationManager notificationManager;

    private LoginView loginView;
    private RegisterView registerView;
    private MainView mainView;

    private User currentUser;
    private final Gson gson = new Gson();

    @Override
    public void start(Stage stage) {
        instance = this;
        this.primaryStage = stage;
        this.primaryStage.setTitle("Messenger");
        this.primaryStage.setMinWidth(900);
        this.primaryStage.setMinHeight(600);

        // Decide transport: WebSocket if baseUrl starts with http/https, else TCP
        String base = AppConfig.getBaseUrl();
        useWebSocket = base.startsWith("http://") || base.startsWith("https://");

        themeManager = new ThemeManager();
        notificationManager = new NotificationManager();

        loginView = new LoginView(this);
        registerView = new RegisterView(this);

        switchToLogin();
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            new Thread(() -> {
                disconnect();
                notificationManager.shutdown();
                Platform.exit();
            }, "app-shutdown").start();
            e.consume();
        });
    }

    public void connectToServer(String host, int port) {
        disconnect();

        if (useWebSocket) {
            // === WebSocket transport (ngrok / production) ===
            wsClient = new RealtimeClient();
            wsClient.setListener(new RealtimeClient.Listener() {
                @Override public void onConnected() {
                    Platform.runLater(() -> {
                        if (loginView != null) loginView.onConnected();
                        if (registerView != null) registerView.onConnected();
                    });
                }
                @Override public void onDisconnected() {
                    Platform.runLater(() -> {
                        currentUser = null;
                        if (mainView != null) mainView.onDisconnected();
                        switchToLogin();
                    });
                }
                @Override public void onMessage(JsonObject message) {
                    Platform.runLater(() -> handleServerMessage(message));
                }
                @Override public void onError(String error) {
                    Platform.runLater(() -> {
                        if (loginView != null) loginView.onError(error);
                        if (registerView != null) registerView.onError(error);
                    });
                }
            });
            wsClient.connect();

        } else {
            // === Legacy TCP transport (local dev) ===
            tcpConnection = new ServerConnection(host, port);
            tcpConnection.setListener(new ServerConnection.ServerListener() {
                @Override public void onConnected() {
                    Platform.runLater(() -> {
                        if (loginView != null) loginView.onConnected();
                        if (registerView != null) registerView.onConnected();
                    });
                }
                @Override public void onDisconnected() {
                    Platform.runLater(() -> {
                        currentUser = null;
                        if (mainView != null) mainView.onDisconnected();
                        switchToLogin();
                    });
                }
                @Override public void onMessage(JsonObject message) {
                    Platform.runLater(() -> handleServerMessage(message));
                }
                @Override public void onError(String error) {
                    Platform.runLater(() -> {
                        if (loginView != null) loginView.onError(error);
                        if (registerView != null) registerView.onError(error);
                    });
                }
            });
            new Thread(() -> tcpConnection.connect(), "server-connector").start();
        }
    }

    private void handleServerMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";

        if (Protocol.TYPE_SUCCESS.equals(type) && mainView == null) {
            JsonObject data = message.has("data") ? message.getAsJsonObject("data") : null;
            if (data != null && data.has("id")) {
                User user = gson.fromJson(data, User.class);
                switchToMain(user);
                return;
            }
        }

        if (Protocol.TYPE_ERROR.equals(type) && mainView == null) {
            String msg = message.has("message") ? message.get("message").getAsString() : "Unknown error";
            if (loginView != null) loginView.showError(msg);
            if (registerView != null) registerView.showError(msg);
            return;
        }

        if (mainView != null) mainView.onServerMessage(message);
    }

    public void disconnect() {
        if (wsClient != null) { wsClient.disconnect(); wsClient = null; }
        if (tcpConnection != null) { tcpConnection.disconnect(); tcpConnection = null; }
    }

    public void logout() {
        send(Protocol.TYPE_LOGOUT, null);
        disconnect();
        switchToLogin();
    }

    public void send(JsonObject message) {
        if (wsClient != null && wsClient.isConnected()) {
            wsClient.send(message);
        } else if (tcpConnection != null && tcpConnection.isConnected()) {
            tcpConnection.send(message);
        }
    }

    public void send(String type, JsonObject data) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        if (data != null) message.add("data", data);
        send(message);
    }

    public boolean isConnected() {
        if (wsClient != null) return wsClient.isConnected();
        if (tcpConnection != null) return tcpConnection.isConnected();
        return false;
    }

    public void switchToLogin() {
        currentUser = null;
        mainView = null;
        primaryStage.setTitle("Messenger");
        Scene scene = loginView.createScene();
        themeManager.applyTo(scene);
        primaryStage.setScene(scene);
        primaryStage.setWidth(450);
        primaryStage.setHeight(600);
        primaryStage.centerOnScreen();
        // Reconnect if not already connected
        if (!isConnected()) {
            if (useWebSocket) {
                // WebSocket: host/port unused, read from app.properties
                connectToServer("", 0);
            } else {
                // Legacy TCP: hardcode localhost (dev only)
                connectToServer("127.0.0.1", Protocol.TCP_PORT);
            }
        }
    }

    public void switchToRegister() {
        Scene scene = registerView.createScene();
        themeManager.applyTo(scene);
        primaryStage.setScene(scene);
        primaryStage.setWidth(450);
        primaryStage.setHeight(650);
        primaryStage.centerOnScreen();
    }

    public void switchToMain(User user) {
        this.currentUser = user;
        mainView = new MainView(this, user);
        Scene scene = mainView.createScene();
        themeManager.applyTo(scene);
        primaryStage.setScene(scene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(700);
        primaryStage.centerOnScreen();
        primaryStage.setTitle("Messenger \u2014 " + user.getDisplayName());
    }

    public ThemeManager getThemeManager() { return themeManager; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public User getCurrentUser() { return currentUser; }
    public Stage getPrimaryStage() { return primaryStage; }
    public static ChatClient getInstance() { return instance; }

    public static void main(String[] args) {
        launch(args);
    }
}
