package com.messenger.client.ui.tabs;

import com.google.gson.JsonObject;
import com.messenger.client.ChatClient;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Tab 3: Trang cá nhân.
 * Hiển thị ảnh đại diện (hỗ trợ ảnh thật hoặc chữ viết tắt),
 * ảnh bìa, tên hiển thị, ID/Username.
 */
public class ProfileTab {

    private final ChatClient client;
    private final User currentUser;

    private Label displayNameLabel;
    private Label usernameLabel;
    private Label userIdLabel;
    private Label presenceLabel;
    private StackPane avatarPane;

    public interface Callback {
        void showAlert(String title, String msg);
    }
    private Callback callback;

    public ProfileTab(ChatClient client, User currentUser, Callback callback) {
        this.client = client;
        this.currentUser = currentUser;
        this.callback = callback;
    }

    public javafx.scene.Node build() {
        VBox root = new VBox(0);
        root.getStyleClass().add("profile-root");

        // --- Cover photo area with overlay button ---
        StackPane coverArea = new StackPane();
        coverArea.setMinHeight(200);
        coverArea.setMaxHeight(200);
        coverArea.getStyleClass().add("profile-cover");
        coverArea.setStyle("-fx-background-color: linear-gradient(to bottom, #0084FF, #0055CC);");

        Button coverBtn = new Button("\uD83D\uDCF7 \u0110\u1ED5i \u1EA3nh b\u00ECa");
        coverBtn.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 6 16; -fx-font-size: 12px; -fx-cursor: hand;");
        coverBtn.setOnAction(e -> showCoverDialog());
        StackPane.setAlignment(coverBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(coverBtn, new Insets(0, 16, 16, 0));
        coverArea.getChildren().add(coverBtn);

        // --- Avatar with camera overlay ---
        avatarPane = buildProfileAvatar();
        StackPane.setMargin(avatarPane, new Insets(-50, 0, 0, 0));
        StackPane.setAlignment(avatarPane, Pos.TOP_CENTER);

        Button avatarBtn = new Button("\uD83D\uDCF7");
        avatarBtn.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 14; -fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; -fx-font-size: 14px; -fx-padding: 0; -fx-cursor: hand;");
        avatarBtn.setOnAction(e -> showAvatarDialog());
        StackPane.setAlignment(avatarBtn, Pos.BOTTOM_CENTER);
        StackPane.setMargin(avatarBtn, new Insets(0, 0, -4, 0));

        StackPane avatarStack = new StackPane(avatarPane, avatarBtn);

        StackPane headerStack = new StackPane(coverArea, avatarStack);
        StackPane.setAlignment(avatarStack, Pos.BOTTOM_CENTER);

        // --- Info section ---
        VBox infoBox = new VBox(6);
        infoBox.setPadding(new Insets(70, 30, 30, 30));
        infoBox.setAlignment(Pos.CENTER);
        infoBox.getStyleClass().add("profile-info");

        // Name row with edit icon
        HBox nameRow = new HBox(6);
        nameRow.setAlignment(Pos.CENTER);
        displayNameLabel = new Label(currentUser.getDisplayName());
        displayNameLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 22));
        displayNameLabel.getStyleClass().add("chat-header-name");
        Button editNameBtn = new Button("\u270F\uFE0F");
        editNameBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-cursor: hand; -fx-font-size: 14px; -fx-padding: 2;");
        editNameBtn.setOnAction(e -> showNameDialog());
        nameRow.getChildren().addAll(displayNameLabel, editNameBtn);

        usernameLabel = new Label("@" + currentUser.getUsername());
        usernameLabel.setFont(Font.font("SansSerif", 14));
        usernameLabel.setStyle("-fx-text-fill: #65676B;");

        userIdLabel = new Label("ID: " + currentUser.getId());
        userIdLabel.setFont(Font.font("SansSerif", 12));
        userIdLabel.setStyle("-fx-text-fill: #888;");

        presenceLabel = new Label(presenceText(currentUser));
        presenceLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        presenceLabel.getStyleClass().add("profile-presence");

        Button updateProfileBtn = new Button("Update Profile");
        updateProfileBtn.getStyleClass().add("button-primary");
        updateProfileBtn.setOnAction(e -> showNameDialog());

        infoBox.getChildren().addAll(nameRow, usernameLabel, userIdLabel, presenceLabel, updateProfileBtn);

        VBox content = new VBox(0, headerStack, infoBox);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-message-list");

        return scroll;
    }

    /**
     * Tạo avatar profile: hiển thị ảnh nếu có avatarUrl, nếu không thì chữ viết tắt.
     */
    private StackPane buildProfileAvatar() {
        StackPane sp = new StackPane();
        sp.getStyleClass().add("profile-avatar");
        sp.setMinSize(104, 104);
        sp.setMaxSize(104, 104);

        // White border circle (always visible)
        Circle border = new Circle(52);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.WHITE);
        border.setStrokeWidth(4);

        String avatarUrl = currentUser.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                ImageView iv = new ImageView(new Image(avatarUrl, 100, 100, true, true, true));
                iv.setFitWidth(100);
                iv.setFitHeight(100);
                Circle clip = new Circle(50, 50, 50);
                iv.setClip(clip);
                sp.getChildren().addAll(iv, border);
                return sp;
            } catch (Exception ignored) {}
        }

        // Fallback: initials + colored circle
        String initials = currentUser.getDisplayName().length() >= 2
                ? currentUser.getDisplayName().substring(0, 2).toUpperCase()
                : currentUser.getDisplayName().substring(0, 1).toUpperCase();
        Circle bg = new Circle(50, Color.rgb(0, 132, 255));
        Label initLabel = new Label(initials);
        initLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 36));
        initLabel.setTextFill(Color.WHITE);
        sp.getChildren().addAll(bg, initLabel, border);
        return sp;
    }

    private void showNameDialog() {
        TextInputDialog d = new TextInputDialog(currentUser.getDisplayName());
        d.setTitle("\u0110\u1ED5i t\u00EAn hi\u1EC3n th\u1ECB");
        d.setHeaderText("Nh\u1EADp t\u00EAn hi\u1EC3n th\u1ECB m\u1EDBi");
        d.showAndWait().ifPresent(n -> {
            if (!n.trim().isEmpty()) {
                JsonObject data = new JsonObject();
                data.addProperty("displayName", n.trim());
                client.send(Protocol.TYPE_UPDATE_PROFILE, data);
                currentUser.setDisplayName(n.trim());
                updateUserInfo(currentUser);
            }
        });
    }

    /**
     * Chọn file ảnh từ máy, chuyển thành data URI và gửi lên server.
     */
    private void showAvatarDialog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Ch\u1ECDn \u1EA3nh \u0111\u1EA1i di\u1EC7n");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fc.showOpenDialog(client.getPrimaryStage());
        if (file == null) return;

        try {
            // Đọc file thành data URI
            byte[] bytes = readAllBytes(file);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String ext = getExtension(file.getName()).toLowerCase();
            String mime = ext.equals("jpg") || ext.equals("jpeg") ? "image/jpeg"
                        : ext.equals("gif") ? "image/gif"
                        : ext.equals("bmp") ? "image/bmp"
                        : "image/png";
            String dataUri = "data:" + mime + ";base64," + base64;

            // Gửi lên server
            JsonObject data = new JsonObject();
            data.addProperty("avatarUrl", dataUri);
            client.send(Protocol.TYPE_UPDATE_PROFILE, data);
            currentUser.setAvatarUrl(dataUri);
            updateUserInfo(currentUser);

            callback.showAlert("\u1EA2nh \u0111\u1EA1i di\u1EC7n", "\u0110\u00E3 c\u1EADp nh\u1EADt \u1EA3nh \u0111\u1EA1i di\u1EC7n!");
        } catch (IOException e) {
            callback.showAlert("L\u1ED7i", "Kh\u00F4ng th\u1EC3 \u0111\u1ECDc file: " + e.getMessage());
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return data;
        }
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "png";
    }

    private void showCoverDialog() {
        TextInputDialog d = new TextInputDialog("");
        d.setTitle("\u0110\u1ED5i \u1EA3nh b\u00ECa");
        d.setHeaderText("Nh\u1EADp URL \u1EA3nh b\u00ECa");
        d.setContentText("(T\u00EDnh n\u0103ng \u0111ang ph\u00E1t tri\u1EC3n \u2014 nh\u1EADp URL \u0111\u1EC3 thay \u0111\u1ED5i m\u00E0u n\u1EC1n)");
        d.showAndWait().ifPresent(url -> {
            callback.showAlert("\u1EA2nh b\u00ECa", "\u0110\u00E3 c\u1EADp nh\u1EADt \u1EA3nh b\u00ECa!");
        });
    }

    /**
     * Update displayed user info after profile edit.
     */
    public void updateUserInfo(User updated) {
        Platform.runLater(() -> {
            displayNameLabel.setText(updated.getDisplayName());
            usernameLabel.setText("@" + updated.getUsername());
            userIdLabel.setText("ID: " + updated.getId());
            if (presenceLabel != null) presenceLabel.setText(presenceText(updated));

            // Rebuild avatar (có thể có ảnh mới)
            StackPane parent = (StackPane) avatarPane.getParent();
            if (parent != null) {
                int idx = parent.getChildren().indexOf(avatarPane);
                parent.getChildren().remove(avatarPane);
                avatarPane = buildProfileAvatar();
                parent.getChildren().add(idx, avatarPane);
            }
        });
    }

    private String presenceText(User user) {
        return "ONLINE".equals(user.getPresence()) ? "\u25CF \u0110ang ho\u1EA1t \u0111\u1ED9ng" : "\u25CB Kh\u00F4ng ho\u1EA1t \u0111\u1ED9ng";
    }

}
