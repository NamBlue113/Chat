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

public class LoginView {

    private final ChatClient client;
    private TextField usernameField;
    private PasswordField passwordField;
    private HBox usernameBox;
    private HBox passwordBox;
    private Label errorLabel;
    private Button loginButton;
    private Label statusLabel;
    private boolean loggingIn = false;

    public LoginView(ChatClient client) {
        this.client = client;
    }

    public Scene createScene() {
        VBox form = new VBox(14);
        form.setAlignment(Pos.CENTER);
        form.getStyleClass().add("form-container");

        StackPane logo = new StackPane();
        logo.getStyleClass().add("app-logo");
        Label logoIcon = new Label("\uD83D\uDCAC");
        logoIcon.getStyleClass().add("app-logo-icon");
        logo.getChildren().add(logoIcon);

        Label title = new Label("Moji Moji");
        title.getStyleClass().add("form-title");

        Label subtitle = new Label("\u0110\u0103ng nh\u1EADp \u0111\u1EC3 ti\u1EBFp t\u1EE5c");
        subtitle.getStyleClass().add("form-subtitle");

        Label usernameLabel = new Label("T\u00EAn \u0111\u0103ng nh\u1EADp");
        usernameLabel.getStyleClass().add("form-label");
        usernameField = new TextField();
        usernameField.setPromptText("Nh\u1EADp t\u00EAn \u0111\u0103ng nh\u1EADp");
        usernameField.getStyleClass().add("field-plain");
        usernameBox = iconField("\uD83D\uDC64", usernameField);

        Label passwordLabel = new Label("M\u1EADt kh\u1EA9u");
        passwordLabel.getStyleClass().add("form-label");
        passwordField = new PasswordField();
        passwordField.setPromptText("Nh\u1EADp m\u1EADt kh\u1EA9u");
        passwordField.getStyleClass().add("field-plain");
        passwordBox = iconField("\uD83D\uDD12", passwordField);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(320);
        errorLabel.setWrapText(true);

        loginButton = new Button("\u0110\u0103ng nh\u1EADp");
        loginButton.getStyleClass().addAll("button", "button-primary");
        loginButton.setMaxWidth(320);
        loginButton.setPrefHeight(42);
        loginButton.setDisable(true);
        loginButton.setOnAction(e -> login());

        statusLabel = new Label("\u0110ang k\u1EBFt n\u1ED1i t\u1EDBi m\u00E1y ch\u1EE7...");
        statusLabel.getStyleClass().add("form-label");

        Hyperlink registerLink = new Hyperlink("Ch\u01B0a c\u00F3 t\u00E0i kho\u1EA3n? \u0110\u0103ng k\u00FD ngay");
        registerLink.getStyleClass().add("form-link");
        registerLink.setOnAction(e -> client.switchToRegister());

        VBox fieldsBlock = new VBox(6, usernameLabel, usernameBox);
        fieldsBlock.setAlignment(Pos.CENTER_LEFT);
        fieldsBlock.setMaxWidth(320);

        VBox passBlock = new VBox(6, passwordLabel, passwordBox);
        passBlock.setAlignment(Pos.CENTER_LEFT);
        passBlock.setMaxWidth(320);

        form.getChildren().addAll(logo, title, subtitle, fieldsBlock, passBlock,
                errorLabel, loginButton, statusLabel, registerLink);

        StackPane root = new StackPane(form);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 480, 660);

        usernameField.textProperty().addListener((obs, old, val) -> updateButtonState());
        passwordField.textProperty().addListener((obs, old, val) -> updateButtonState());
        passwordField.setOnAction(e -> login());
        usernameField.setOnAction(e -> passwordField.requestFocus());
        return scene;
    }

    private HBox iconField(String icon, TextField field) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("input-pill-icon");
        HBox box = new HBox(10, iconLabel, field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("input-pill");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.focusedProperty().addListener((obs, was, isFocused) -> {
            if (isFocused) {
                box.getStyleClass().add("input-pill-focused");
            } else {
                box.getStyleClass().remove("input-pill-focused");
            }
        });
        return box;
    }

    private void updateButtonState() {
        if (loginButton == null) return;
        if (!loggingIn) {
            loginButton.setDisable(!client.isConnected()
                    || usernameField.getText().trim().isEmpty()
                    || passwordField.getText().isEmpty());
        }
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (!client.isConnected()) {
            showError("Ch\u01B0a k\u1EBFt n\u1ED1i \u0111\u01B0\u1EE3c m\u00E1y ch\u1EE7. Ki\u1EC3m tra server/ngrok r\u1ED3i th\u1EED l\u1EA1i.");
            resetLoginButton();
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui l\u00F2ng nh\u1EADp t\u00EAn \u0111\u0103ng nh\u1EADp v\u00E0 m\u1EADt kh\u1EA9u");
            return;
        }
        showError(null);
        loggingIn = true;
        loginButton.setDisable(true);
        loginButton.setText("\u0110ang \u0111\u0103ng nh\u1EADp...");
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        client.send("LOGIN", data);
    }

    public void onConnected() {
        if (statusLabel != null) statusLabel.setText("");
        if (loginButton != null) {
            loggingIn = false;
            loginButton.setText("\u0110\u0103ng nh\u1EADp");
            updateButtonState();
        }
    }

    public void onError(String error) {
        showError(error);
        resetLoginButton();
    }

    public void showError(String msg) {
        if (msg == null || msg.isEmpty()) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        } else {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private void resetLoginButton() {
        loggingIn = false;
        if (loginButton != null) {
            loginButton.setText("\u0110\u0103ng nh\u1EADp");
            updateButtonState();
        }
    }
}
