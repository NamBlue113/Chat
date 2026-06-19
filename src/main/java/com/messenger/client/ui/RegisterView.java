package com.messenger.client.ui;

import com.google.gson.JsonObject;
import com.messenger.client.ChatClient;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class RegisterView {

    private final ChatClient client;
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField displayNameField;
    private Label errorLabel;
    private Button registerButton;

    private boolean serverConnected = false;

    public RegisterView(ChatClient client) {
        this.client = client;
    }

    public Scene createScene() {
        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER);
        form.getStyleClass().add("form-container");

        StackPane logo = new StackPane();
        logo.getStyleClass().add("app-logo");
        Label logoIcon = new Label("\uD83D\uDCAC");
        logoIcon.getStyleClass().add("app-logo-icon");
        logo.getChildren().add(logoIcon);

        Label title = new Label("Tạo tài khoản");
        title.getStyleClass().add("form-title");
        Label subtitle = new Label("Tham gia Messenger ngay hôm nay");
        subtitle.getStyleClass().add("form-subtitle");

        Label usernameLabel = new Label("Tên đăng nhập");
        usernameLabel.getStyleClass().add("form-label");
        usernameField = new TextField();
        usernameField.setPromptText("Chọn một tên đăng nhập");
        usernameField.getStyleClass().add("field-plain");
        HBox usernameBox = iconField("\uD83D\uDC64", usernameField);

        Label displayNameLabel = new Label("Tên hiển thị");
        displayNameLabel.getStyleClass().add("form-label");
        displayNameField = new TextField();
        displayNameField.setPromptText("Tên hiển thị của bạn");
        displayNameField.getStyleClass().add("field-plain");
        HBox displayNameBox = iconField("\uD83D\uDCDD", displayNameField);

        Label passwordLabel = new Label("Mật khẩu");
        passwordLabel.getStyleClass().add("form-label");
        passwordField = new PasswordField();
        passwordField.setPromptText("Tối thiểu 4 ký tự");
        passwordField.getStyleClass().add("field-plain");
        HBox passwordBox = iconField("\uD83D\uDD12", passwordField);

        Label confirmLabel = new Label("Xác nhận mật khẩu");
        confirmLabel.getStyleClass().add("form-label");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu");
        confirmPasswordField.getStyleClass().add("field-plain");
        HBox confirmBox = iconField("\uD83D\uDD12", confirmPasswordField);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(320);
        errorLabel.setWrapText(true);

        registerButton = new Button("Tạo tài khoản");
        registerButton.getStyleClass().addAll("button", "button-primary");
        registerButton.setMaxWidth(320);
        registerButton.setPrefHeight(42);
        registerButton.setOnAction(e -> register());

        // Apply deferred connection state — if server connected before scene was
        // created
        registerButton.setDisable(!serverConnected);

        Hyperlink loginLink = new Hyperlink("Đã có tài khoản? Đăng nhập");
        loginLink.getStyleClass().add("form-link");
        loginLink.setOnAction(e -> client.switchToLogin());

        VBox usernameBlock = labeledBlock(usernameLabel, usernameBox);
        VBox displayNameBlock = labeledBlock(displayNameLabel, displayNameBox);
        VBox passwordBlock = labeledBlock(passwordLabel, passwordBox);
        VBox confirmBlock = labeledBlock(confirmLabel, confirmBox);

        form.getChildren().addAll(logo, title, subtitle, usernameBlock, displayNameBlock,
                passwordBlock, confirmBlock, errorLabel, registerButton, loginLink);

        StackPane root = new StackPane(form);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 480, 760);

        usernameField.textProperty().addListener((obs, old, val) -> updateButtonState());
        passwordField.textProperty().addListener((obs, old, val) -> updateButtonState());
        confirmPasswordField.textProperty().addListener((obs, old, val) -> updateButtonState());
        displayNameField.textProperty().addListener((obs, old, val) -> updateButtonState());
        confirmPasswordField.setOnAction(e -> register());
        return scene;
    }

    private VBox labeledBlock(Label label, HBox box) {
        VBox v = new VBox(6, label, box);
        v.setAlignment(Pos.CENTER_LEFT);
        v.setMaxWidth(320);
        return v;
    }

    /**
     * Wraps a text input inside a rounded "pill" container with a leading icon,
     * and toggles a focus style class on the wrapper so the whole pill highlights.
     */
    private HBox iconField(String icon, TextField field) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("input-pill-icon");
        HBox box = new HBox(10, iconLabel, field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("input-pill");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.focusedProperty().addListener((obs, was, isFocused) -> {
            if (isFocused)
                box.getStyleClass().add("input-pill-focused");
            else
                box.getStyleClass().remove("input-pill-focused");
        });
        return box;
    }

    private void updateButtonState() {
        if (registerButton == null)
            return;
        registerButton.setDisable(!serverConnected
                || usernameField.getText().trim().isEmpty()
                || passwordField.getText().isEmpty()
                || confirmPasswordField.getText().isEmpty());
    }

    private void register() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String displayName = displayNameField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin bắt buộc");
            return;
        }
        if (password.length() < 4) {
            showError("Mật khẩu phải có ít nhất 4 ký tự");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Mật khẩu xác nhận không khớp");
            return;
        }
        showError(null);
        registerButton.setDisable(true);
        registerButton.setText("Đang tạo...");
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        data.addProperty("displayName", displayName.isEmpty() ? username : displayName);
        client.send("REGISTER", data);
    }

    public void onConnected() {
        serverConnected = true;
        if (registerButton != null) {
            registerButton.setDisable(false);
            updateButtonState();
        }
    }

    public void onError(String error) {
        showError(error);
        if (registerButton != null) {
            registerButton.setDisable(false);
            registerButton.setText("Tạo tài khoản");
            updateButtonState();
        }
    }

    public void showError(String msg) {
        if (errorLabel == null) return;
        if (msg == null || msg.isEmpty()) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        } else {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }
}