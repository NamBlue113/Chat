package com.messenger.client.ui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.messenger.client.ChatClient;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

/**
 * Tab 2: Danh bạ & Lời mời kết bạn.
 * Phần trên: lời mời kết bạn đang chờ (Accept / Reject)
 * Phần dưới: danh sách bạn bè theo bảng chữ cái
 */
public class ContactsTab {

    private final ChatClient client;
    private final User currentUser;

    // Friend request panel
    private VBox requestPanel;
    private Label requestCountLabel;
    private final List<JsonObject> pendingRequests = new ArrayList<>();

    // Friend list panel
    private VBox friendListPanel;
    private final List<User> friends = new ArrayList<>();
    private final Map<Character, List<User>> friendsByLetter = new TreeMap<>();

    public interface Callback {
        void onStartChat(long userId, String displayName);
        void showAlert(String title, String msg);
    }
    private final Callback callback;

    public ContactsTab(ChatClient client, User currentUser, Callback callback) {
        this.client = client;
        this.currentUser = currentUser;
        this.callback = callback;
    }

    public javafx.scene.Node build() {
        VBox root = new VBox(0);
        root.setPadding(new Insets(0));
        root.getStyleClass().add("contacts-root");

        // --- Section: Lời mời kết bạn ---
        Label reqHeader = new Label("L\u1EDDi m\u1EDDi k\u1EBFt b\u1EA1n");
        reqHeader.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
        reqHeader.setPadding(new Insets(16, 16, 8, 16));
        reqHeader.getStyleClass().add("sidebar-title");

        requestCountLabel = new Label("0 l\u1EDDi m\u1EDDi \u0111ang ch\u1EDD");
        requestCountLabel.setFont(Font.font("SansSerif", 12));
        requestCountLabel.setPadding(new Insets(0, 16, 4, 16));
        requestCountLabel.setStyle("-fx-text-fill: #65676B;");

        requestPanel = new VBox(4);
        requestPanel.setPadding(new Insets(0, 16, 8, 16));
        requestPanel.getStyleClass().add("contacts-section");

        root.getChildren().addAll(reqHeader, requestCountLabel, requestPanel);

        // Separator
        Separator sep = new Separator();
        sep.setPadding(new Insets(8, 16, 8, 16));
        root.getChildren().add(sep);

        // --- Section: Danh sách bạn bè ---
        Label friendHeader = new Label("Danh s\u00E1ch b\u1EA1n b\u00E8");
        friendHeader.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
        friendHeader.setPadding(new Insets(8, 16, 8, 16));
        friendHeader.getStyleClass().add("sidebar-title");

        friendListPanel = new VBox(4);
        friendListPanel.setPadding(new Insets(0, 16, 16, 16));
        friendListPanel.getStyleClass().add("contacts-section");

        VBox content = new VBox(0, friendHeader, friendListPanel);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-message-list");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        return root;
    }

    /**
     * Nhận và xử lý message từ server.
     */
    public void handleServerMessage(JsonObject m) {
        String type = m.has("type") ? m.get("type").getAsString() : "";
        if (Protocol.TYPE_SUCCESS.equals(type)) {
            JsonObject d = m.has("data") && m.get("data").isJsonObject() ? m.getAsJsonObject("data") : null;
            if (d != null) {
                if (d.has("requests") && d.get("requests").isJsonArray()) {
                    loadPendingRequests(d.getAsJsonArray("requests"));
                }
                if (d.has("friends") && d.get("friends").isJsonArray()) {
                    List<User> friendList = new ArrayList<>();
                    for (JsonElement e : d.getAsJsonArray("friends")) {
                        friendList.add(new com.google.gson.Gson().fromJson(e, User.class));
                    }
                    loadFriends(friendList);
                }
            }
        } else if (Protocol.TYPE_FRIEND_REQUEST.equals(type)) {
            JsonObject d = m.has("data") ? m.getAsJsonObject("data") : m;
            if (d != null) addPendingRequest(d);
        } else if (Protocol.TYPE_FRIEND_RESPONSE.equals(type)) {
            refreshRequestPanel();
        }
    }

    /** Số lượng lời mời kết bạn đang chờ */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * Load pending friend requests from server response.
     */
    public void loadPendingRequests(JsonArray arr) {
        pendingRequests.clear();
        for (JsonElement e : arr) {
            pendingRequests.add(e.getAsJsonObject());
        }
        refreshRequestPanel();
    }

    /**
     * Add a new incoming friend request (real-time signal).
     */
    public void addPendingRequest(JsonObject req) {
        pendingRequests.add(0, req);
        refreshRequestPanel();
    }

    /**
     * Load friends list from server response and sort alphabetically.
     */
    public void loadFriends(List<User> friendList) {
        friends.clear();
        friendsByLetter.clear();
        friends.addAll(friendList);
        friends.sort(Comparator.comparing(u -> u.getDisplayName().toLowerCase()));

        for (User u : friends) {
            char letter = Character.toUpperCase(u.getDisplayName().charAt(0));
            friendsByLetter.computeIfAbsent(letter, k -> new ArrayList<>()).add(u);
        }
        refreshFriendPanel();
    }

    /**
     * Accept a friend request.
     */
    public void acceptRequest(JsonObject req) {
        long fromUserId = req.get("senderId").getAsLong();
        JsonObject data = new JsonObject();
        data.addProperty("fromUserId", fromUserId);
        data.addProperty("action", "accept");
        client.send(Protocol.TYPE_FRIEND_RESPONSE, data);
        pendingRequests.remove(req);
        refreshRequestPanel();
        client.send(Protocol.TYPE_FRIEND_LIST, new JsonObject()); // refresh friend list
        client.send(Protocol.TYPE_GET_FRIEND_REQUESTS, new JsonObject());
    }

    /**
     * Reject a friend request.
     */
    public void rejectRequest(JsonObject req) {
        long fromUserId = req.get("senderId").getAsLong();
        JsonObject data = new JsonObject();
        data.addProperty("fromUserId", fromUserId);
        data.addProperty("action", "reject");
        client.send(Protocol.TYPE_FRIEND_RESPONSE, data);
        pendingRequests.remove(req);
        refreshRequestPanel();
        client.send(Protocol.TYPE_GET_FRIEND_REQUESTS, new JsonObject());
    }

    // --- Internal ---

    private void refreshRequestPanel() {
        Platform.runLater(() -> {
            requestPanel.getChildren().clear();
            int count = pendingRequests.size();
            requestCountLabel.setText(count + " l\u1EDDi m\u1EDDi \u0111ang ch\u1EDD");

            if (count == 0) {
                requestPanel.getChildren().add(emptyLabel("Kh\u00F4ng c\u00F3 l\u1EDDi m\u1EDDi n\u00E0o"));
                return;
            }

            for (JsonObject req : pendingRequests) {
                String name = req.has("senderDisplayName") ? req.get("senderDisplayName").getAsString() : "Unknown";
                String username = req.has("senderUsername") ? req.get("senderUsername").getAsString() : "";
                HBox row = createRequestRow(name, username, req);
                requestPanel.getChildren().add(row);
            }
        });
    }

    private HBox createRequestRow(String name, String username, JsonObject req) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        row.getStyleClass().addAll("contact-row", "request-row");

        // Avatar
        String in = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.substring(0, 1).toUpperCase();
        Color ac = Color.rgb((int)(Math.random()*200+55), (int)(Math.random()*200+55), (int)(Math.random()*200+55));
        StackPane av = miniAvatar(in, ac, 40);

        VBox info = new VBox(2);
        Label nl = new Label(name);
        nl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        nl.getStyleClass().add("chat-header-name");
        Label ul = new Label("@" + username);
        ul.setFont(Font.font("SansSerif", 11));
        ul.setStyle("-fx-text-fill: #65676B;");
        info.getChildren().addAll(nl, ul);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button accept = new Button("Ch\u1EA5p nh\u1EADn");
        accept.getStyleClass().add("button-primary");
        accept.setStyle("-fx-background-radius: 16; -fx-padding: 4 14; -fx-font-size: 12px;");
        accept.setOnAction(e -> acceptRequest(req));

        Button reject = new Button("T\u1EEB ch\u1ED1i");
        reject.setStyle("-fx-background-color: #3A3D52; -fx-text-fill: #E0E0E0; -fx-background-radius: 16; -fx-padding: 4 14; -fx-font-size: 12px;");
        reject.setOnAction(e -> rejectRequest(req));

        row.getChildren().addAll(av, info, accept, reject);
        return row;
    }

    private void refreshFriendPanel() {
        Platform.runLater(() -> {
            friendListPanel.getChildren().clear();
            if (friends.isEmpty()) {
                friendListPanel.getChildren().add(emptyLabel("Ch\u01B0a c\u00F3 b\u1EA1n b\u00E8 n\u00E0o"));
                return;
            }
            for (Map.Entry<Character, List<User>> entry : friendsByLetter.entrySet()) {
                Label letterLabel = new Label(String.valueOf(entry.getKey()));
                letterLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
                letterLabel.setPadding(new Insets(8, 0, 4, 0));
                letterLabel.setStyle("-fx-text-fill: #0084FF;");
                friendListPanel.getChildren().add(letterLabel);

                for (User u : entry.getValue()) {
                    HBox row = createFriendRow(u);
                    friendListPanel.getChildren().add(row);
                }
            }
        });
    }

    private HBox createFriendRow(User u) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 4, 6, 4));
        row.getStyleClass().add("contact-row");
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> callback.onStartChat(u.getId(), u.getDisplayName()));

        String in = u.getDisplayName().length() >= 2 ? u.getDisplayName().substring(0, 2).toUpperCase() : u.getDisplayName().substring(0, 1).toUpperCase();
        Color ac = Color.rgb((int)(Math.abs(u.getId()) % 200 + 55), (int)(Math.abs(u.getId()*3) % 200 + 55), (int)(Math.abs(u.getId()*7) % 200 + 55));
        StackPane av = miniAvatar(in, ac, 40);

        VBox info = new VBox(2);
        Label nl = new Label(u.getDisplayName());
        nl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        nl.getStyleClass().add("chat-header-name");
        Label sl = new Label("ONLINE".equals(u.getPresence()) ? "\u25CF \u0110ang ho\u1EA1t \u0111\u1ED9ng" : "Kh\u00F4ng ho\u1EA1t \u0111\u1ED9ng");
        sl.setFont(Font.font("SansSerif", 11));
        sl.setStyle("ONLINE".equals(u.getPresence()) ? "-fx-text-fill: #31A24C;" : "-fx-text-fill: #65676B;");
        info.getChildren().addAll(nl, sl);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button chat = new Button("Chat");
        chat.getStyleClass().add("button-primary");
        chat.setStyle("-fx-background-radius: 16; -fx-padding: 4 14; -fx-font-size: 12px;");
        chat.setOnAction(e -> {
            e.consume();
            callback.onStartChat(u.getId(), u.getDisplayName());
        });

        row.getChildren().addAll(av, info, chat);
        return row;
    }

    private StackPane miniAvatar(String initials, Color color, int size) {
        Circle bg = new Circle(size / 2.0, color);
        Label label = new Label(initials);
        label.setFont(Font.font("SansSerif", FontWeight.BOLD, size / 3.0));
        label.setTextFill(Color.WHITE);
        StackPane sp = new StackPane(bg, label);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        return sp;
    }

    private Label emptyLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", 13));
        l.setStyle("-fx-text-fill: #65676B;");
        l.setPadding(new Insets(20, 0, 0, 0));
        l.setAlignment(Pos.CENTER);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }
}
