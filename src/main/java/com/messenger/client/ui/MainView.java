package com.messenger.client.ui;

import com.google.gson.*;
import com.messenger.client.ChatClient;
import com.messenger.client.call.VoiceCallHandler;
import com.messenger.client.call.VideoStreamHandler;
import com.messenger.client.ui.tabs.ChatTab;
import com.messenger.client.ui.tabs.ContactsTab;
import com.messenger.client.ui.tabs.ProfileTab;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

/**
 * Main View — Giao diện Zalo nâng cao (dựa trên layout Main1).
 *
 * Bố cục 4 cột:
 * ┌──────────┬────────────────┬──────────────────────────┬───────────────┐
 * │ NavRail │ Sidebar │ Content Area (chat) │ Right Panel │
 * │ (68px) │ (320px) │ (co giãn) │ (280px) │
 * │ avatar │ conversation │ │ Thông tin hội │
 * │ nav btn │ list │ │ thoại │
 * │ settings │ │ │ │
 * └──────────┴────────────────┴──────────────────────────┴───────────────┘
 *
 * Tính năng giữ nguyên từ MainView gốc:
 * - Full server message routing (chat, contacts, search, presence, friend
 * request…)
 * - Contacts badge (pending friend requests)
 * - onSearch() — global user search
 * - onStartChat() — mở chat từ ContactsTab
 * - VoiceCall / VideoCall handlers
 * - Theme toggle (isDarkMode + ThemeManager CSS)
 * - showAlert()
 * - getChatTab() / getContactsTab() / getProfileTab()
 */
public class MainView implements ChatTab.Callback, ContactsTab.Callback {

    private final ChatClient client;
    private final User currentUser;
    private final Gson gson = new Gson();

    // Tab controllers
    private ChatTab chatTab;
    private ContactsTab contactsTab;
    private ProfileTab profileTab;

    // Layout chính — 4 cột theo Zalo
    private HBox mainShell;
    private VBox navRail;
    private Region sidebarRegion;
    private StackPane contentArea;
    private VBox rightInfoPanel;

    // View nodes
    private javafx.scene.Node chatView;
    private javafx.scene.Node contactsView;
    private javafx.scene.Node profileView;

    // Navigation
    private Label contactsBadge;
    private ToggleGroup navGroup;
    private ToggleButton chatNavBtn, contactsNavBtn, profileNavBtn;
    private ContextMenu settingsMenu;

    // Message context menu (giữ nguyên từ Main1)
    private ContextMenu messageFullMenu;
    private long currentSelectedMsgId = -1;

    // Theme state
    private boolean isDarkMode = true;

    // Call handlers
    private VoiceCallHandler voiceCallHandler;
    private VideoStreamHandler videoStreamHandler;
    private ImageView remoteVideoView;
    private ImageView localVideoView;

    private Scene scene;

    public MainView(ChatClient client, User currentUser) {
        this.client = client;
        this.currentUser = currentUser;
    }

    // ==================== Scene Creation ====================

    public Scene createScene() {
        // Khởi tạo các tab controller
        chatTab = new ChatTab(client, currentUser, this);
        contactsTab = new ContactsTab(client, currentUser, this);
        profileTab = new ProfileTab(client, currentUser, (title, msg) -> showAlert(title, msg));

        // Lấy các node hiển thị
        javafx.scene.Node sidebar = chatTab.getSidebarNode();
        chatView = chatTab.getChatAreaNode();
        contactsView = contactsTab.build();
        profileView = profileTab.build();

        // Content area (hoán đổi giữa các view)
        contentArea = new StackPane(chatView);
        contentArea.setMinWidth(0);
        if (chatView instanceof Region) ((Region) chatView).setMinWidth(0);
        if (contactsView instanceof Region) ((Region) contactsView).setMinWidth(0);
        if (profileView instanceof Region) ((Region) profileView).setMinWidth(0);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        // NavRail dọc bên trái (Zalo style)
        navRail = createZaloNavRail(sidebar);

        // Bọc sidebar
        if (sidebar instanceof Region) {
            sidebarRegion = (Region) sidebar;
            sidebarRegion.setPrefWidth(320);
            sidebarRegion.setMinWidth(220);
            sidebarRegion.setMaxWidth(360);
        }

        // Panel thông tin hội thoại bên phải
        rightInfoPanel = createRightConversationInfoPanel();

        // Ghép layout 4 cột
        mainShell = new HBox();
        mainShell.getChildren().addAll(navRail, sidebarRegion, contentArea, rightInfoPanel);

        // Khởi tạo message action menu
        buildMessageActionMenus();

        // Áp dụng màu nền ban đầu
        applyThemeStyles();

        scene = new Scene(mainShell, 1280, 800);
        installResponsiveLayout();

        // Yêu cầu dữ liệu ban đầu từ server
        client.send(Protocol.TYPE_FRIEND_LIST, new JsonObject());
        client.send(Protocol.TYPE_GROUP_LIST, new JsonObject());
        client.send(Protocol.TYPE_GET_FRIEND_REQUESTS, new JsonObject());

        return scene;
    }

    private void installResponsiveLayout() {
        scene.widthProperty().addListener((obs, oldWidth, newWidth) -> updateResponsiveLayout(newWidth.doubleValue()));
        Platform.runLater(() -> updateResponsiveLayout(scene.getWidth()));
    }

    private void updateResponsiveLayout(double width) {
        boolean compact = width < 1120;
        boolean veryCompact = width < 980;

        if (navRail != null) {
            double navWidth = veryCompact ? 56 : 68;
            navRail.setMinWidth(navWidth);
            navRail.setPrefWidth(navWidth);
            navRail.setMaxWidth(navWidth);
        }

        if (sidebarRegion != null) {
            double sidebarWidth = veryCompact ? 240 : compact ? 280 : 320;
            sidebarRegion.setMinWidth(veryCompact ? 220 : 240);
            sidebarRegion.setPrefWidth(sidebarWidth);
            sidebarRegion.setMaxWidth(compact ? sidebarWidth : 360);
        }

        if (rightInfoPanel != null) {
            if (compact) {
                rightInfoPanel.setVisible(false);
                rightInfoPanel.setManaged(false);
                rightInfoPanel.setPrefWidth(0);
                rightInfoPanel.setMaxWidth(0);
            } else if (rightPanelExpanded) {
                rightInfoPanel.setVisible(true);
                rightInfoPanel.setManaged(true);
                rightInfoPanel.setPrefWidth(260);
                rightInfoPanel.setMaxWidth(260);
            }
        }
    }

    // ==================== Theme ====================

    /**
     * Áp dụng màu nền động cho từng vùng theo trạng thái Sáng / Tối.
     */
    private void applyThemeStyles() {
        if (isDarkMode) {
            mainShell.setStyle("-fx-background-color: #101114;");
            navRail.setStyle("-fx-background-color: #191A1F; -fx-border-color: #2F3036; -fx-border-width: 0 1 0 0;");
            if (sidebarRegion != null) {
                sidebarRegion.setStyle(
                        "-fx-background-color: #202127; -fx-border-color: #2F3036; -fx-border-width: 0 1 0 0;");
            }
            contentArea.setStyle("-fx-background-color: #101114;");
            rightInfoPanel
                    .setStyle("-fx-background-color: #191A1F; -fx-border-color: #2F3036; -fx-border-width: 0 0 0 1;");
        } else {
            mainShell.setStyle("-fx-background-color: #EBF1F6;");
            navRail.setStyle("-fx-background-color: #DBE3EC; -fx-border-color: #CCD4DB; -fx-border-width: 0 1 0 0;");
            if (sidebarRegion != null) {
                sidebarRegion.setStyle(
                        "-fx-background-color: #EBF1F6; -fx-border-color: #CCD4DB; -fx-border-width: 0 1 0 0;");
            }
            // Vùng chat giữa → nền trắng tinh (Light Mode)
            contentArea
                    .setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CCD4DB; -fx-border-width: 0 1 0 0;");
            rightInfoPanel
                    .setStyle("-fx-background-color: #EBF1F6; -fx-border-color: #CCD4DB; -fx-border-width: 0 0 0 1;");
        }
    }

    @Override
    public void onThemeToggle() {
        isDarkMode = !isDarkMode;
        applyThemeStyles();
        // Vẫn gọi ThemeManager để cập nhật CSS stylesheet (giữ nguyên từ MainView gốc)
        if (client.getThemeManager() != null) {
            client.getThemeManager().toggle(scene);
        }
    }

    // ==================== NavRail (Zalo style) ====================

    /**
     * Tạo thanh điều hướng dọc 68px bên trái, gồm: avatar, nav buttons, settings
     * button.
     */
    private VBox createZaloNavRail(javafx.scene.Node sidebar) {
        VBox rail = new VBox();
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(20, 0, 20, 0));
        rail.setPrefWidth(68);

        // Avatar chữ cái đầu
        String shortName = currentUser.getDisplayName()
                .substring(0, Math.min(2, currentUser.getDisplayName().length()))
                .toUpperCase();
        Label userAvatar = new Label(shortName);
        userAvatar.setAlignment(Pos.CENTER);
        userAvatar.setPrefSize(44, 44);
        userAvatar.setStyle(
                "-fx-background-color: #A156FF; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 50;");
        VBox.setMargin(userAvatar, new Insets(0, 0, 20, 0));
        rail.getChildren().add(userAvatar);

        // Nav buttons giữa (co giãn theo chiều dọc)
        VBox midNavContainer = new VBox(12);
        midNavContainer.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(midNavContainer, Priority.ALWAYS);

        navGroup = new ToggleGroup();

        chatNavBtn = createZaloNavButton("\uD83D\uDCAC", "Tin nhắn");
        chatNavBtn.setSelected(true);
        chatNavBtn.setOnAction(e -> switchContentView(chatView));

        contactsNavBtn = createZaloNavButton("\uD83D\uDC65", "Danh bạ");
        contactsNavBtn.setOnAction(e -> {
            switchContentView(contactsView);
            // Refresh danh sách bạn bè & lời mời (giữ nguyên từ MainView gốc)
            client.send(Protocol.TYPE_GET_FRIEND_REQUESTS, new JsonObject());
            client.send(Protocol.TYPE_FRIEND_LIST, new JsonObject());
        });

        profileNavBtn = createZaloNavButton("\uD83D\uDC64", "Cá nhân");
        profileNavBtn.setOnAction(e -> switchContentView(profileView));

        // Badge lời mời kết bạn
        contactsBadge = new Label();
        contactsBadge.setStyle(
                "-fx-background-color: #FF3B30; -fx-text-fill: white; " +
                        "-fx-font-size: 10px; -fx-font-weight: bold; " +
                        "-fx-background-radius: 10; -fx-padding: 1 5 1 5;");
        contactsBadge.setVisible(false);

        StackPane contactsStack = new StackPane(contactsNavBtn, contactsBadge);
        StackPane.setAlignment(contactsBadge, Pos.TOP_RIGHT);

        midNavContainer.getChildren().addAll(chatNavBtn, contactsStack, profileNavBtn);
        rail.getChildren().add(midNavContainer);

        // Settings button dưới cùng
        Button settingsBtn = new Button("⚙");
        settingsBtn.setPrefSize(48, 48);
        settingsBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #A0A5B1; " +
                        "-fx-font-size: 24px; -fx-cursor: hand;");
        buildZaloSettingsMenu(settingsBtn);
        settingsBtn.setOnAction(e -> settingsMenu.show(settingsBtn, javafx.geometry.Side.RIGHT, 5, -180));
        rail.getChildren().add(settingsBtn);

        // Ẩn userBar cũ trong sidebar (không cần nữa vì navRail đã thay thế)
        hideSidebarUserBar(sidebar);

        return rail;
    }

    /**
     * Ẩn userBar (HBox cuối) trong sidebar gốc của ChatTab vì navRail đã thay thế.
     */
    private void hideSidebarUserBar(javafx.scene.Node sidebar) {
        if (!(sidebar instanceof VBox))
            return;
        VBox sb = (VBox) sidebar;
        if (sb.getChildren().isEmpty())
            return;
        javafx.scene.Node last = sb.getChildren().get(sb.getChildren().size() - 1);
        if (last instanceof HBox) {
            last.setVisible(false);
            last.setManaged(false);
        }
    }

    private ToggleButton createZaloNavButton(String icon, String tooltip) {
        ToggleButton btn = new ToggleButton(icon);
        btn.setToggleGroup(navGroup);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setPrefSize(48, 48);
        btn.setStyle(
                "-fx-background-color: transparent; -fx-background-radius: 10; " +
                        "-fx-font-size: 20px; -fx-text-fill: #A0A5B1; -fx-cursor: hand;");
        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                btn.setStyle(
                        "-fx-background-color: #8228FF; -fx-background-radius: 10; " +
                                "-fx-font-size: 20px; -fx-text-fill: #FFFFFF;");
            } else {
                btn.setStyle(
                        "-fx-background-color: transparent; -fx-background-radius: 10; " +
                                "-fx-font-size: 20px; -fx-text-fill: #A0A5B1;");
            }
        });
        return btn;
    }

    // ==================== Settings Menu ====================

    private void buildZaloSettingsMenu(Button anchor) {
        settingsMenu = new ContextMenu();
        // Gọn hơn: font nhỏ, padding chặt, không dàn trải
        settingsMenu.setStyle(
                "-fx-background-color: #25272D; -fx-border-color: #3A3D46; " +
                        "-fx-background-radius: 6; -fx-font-size: 12px;");

        String itemStyle = "-fx-text-fill: #D0D3DA; -fx-padding: 4 14 4 10;";

        MenuItem accountInfo = new MenuItem("👤  Tài khoản");
        accountInfo.setStyle(itemStyle);
        accountInfo.setOnAction(e -> {
            switchContentView(profileView);
            profileNavBtn.setSelected(true);
        });

        MenuItem themeToggle = new MenuItem("🌓  Sáng / Tối");
        themeToggle.setStyle(itemStyle);
        themeToggle.setOnAction(e -> onThemeToggle());

        MenuItem logoutItem = new MenuItem("🚪  Đăng xuất");
        logoutItem.setStyle("-fx-text-fill: #FF453A; -fx-font-weight: bold; -fx-padding: 4 14 4 10;");
        logoutItem.setOnAction(e -> onLogout());

        settingsMenu.getItems().addAll(accountInfo, themeToggle, new SeparatorMenuItem(), logoutItem);
    }

    // ==================== Right Info Panel ====================

    private boolean rightPanelExpanded = true;

    private VBox createRightConversationInfoPanel() {
        VBox panel = new VBox();
        panel.setPrefWidth(260);
        panel.setMinWidth(0);
        panel.setMaxWidth(260);

        // ── Header: tiêu đề + nút thu gọn ────────────────────────────
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 8, 10, 14));
        header.setStyle("-fx-border-color: #2F3036; -fx-border-width: 0 0 1 0;");

        Label titleLbl = new Label("Ảnh / Video đã gửi");
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titleLbl.setTextFill(Color.web("#D0D3DA"));
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        Button collapseBtn = new Button("›");
        collapseBtn.setPrefSize(24, 24);
        collapseBtn.setStyle(
                "-fx-background-color: #2A2B32; -fx-text-fill: #A0A5B1; " +
                        "-fx-font-size: 13px; -fx-font-weight: bold; " +
                        "-fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: transparent;");
        collapseBtn.setTooltip(new Tooltip("Thu gọn"));
        collapseBtn.setOnAction(e -> toggleRightPanel(panel, collapseBtn));

        header.getChildren().addAll(titleLbl, collapseBtn);

        // ── Media grid 3×3 ────────────────────────────────────────────
        VBox mediaBox = new VBox(8);
        mediaBox.setPadding(new Insets(12));

        GridPane mediaGrid = new GridPane();
        mediaGrid.setHgap(5);
        mediaGrid.setVgap(5);
        double thumbSize = 72;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                VBox thumb = new VBox();
                thumb.setPrefSize(thumbSize, thumbSize);
                thumb.setStyle(
                        "-fx-background-color: #2A2B32; -fx-background-radius: 4; " +
                                "-fx-border-color: #3A3D46; -fx-border-radius: 4;");
                mediaGrid.add(thumb, col, row);
            }
        }

        Button viewAllBtn = new Button("Xem tất cả");
        viewAllBtn.setMaxWidth(Double.MAX_VALUE);
        viewAllBtn.setStyle(
                "-fx-background-color: #2A2B32; -fx-text-fill: #A156FF; " +
                        "-fx-font-size: 11px; -fx-font-weight: bold; " +
                        "-fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 5 0 5 0;");

        mediaBox.getChildren().addAll(mediaGrid, viewAllBtn);
        panel.getChildren().addAll(header, mediaBox);
        return panel;
    }

    /**
     * Thu gọn / mở rộng right panel.
     * Khi thu gọn: panel ẩn, nút "‹" nổi trên góc phải content area để mở lại.
     */
    private void toggleRightPanel(VBox panel, Button collapseBtn) {
        rightPanelExpanded = !rightPanelExpanded;

        if (rightPanelExpanded) {
            panel.setPrefWidth(260);
            panel.setMaxWidth(260);
            panel.setVisible(true);
            panel.setManaged(true);
        } else {
            panel.setPrefWidth(0);
            panel.setMaxWidth(0);
            panel.setVisible(false);
            panel.setManaged(false);

            // Nút nổi "‹" để mở lại
            Button expandBtn = new Button("‹");
            expandBtn.setPrefSize(20, 36);
            expandBtn.setStyle(
                    "-fx-background-color: #25272D; -fx-text-fill: #A0A5B1; " +
                            "-fx-font-size: 13px; -fx-background-radius: 4 0 0 4; " +
                            "-fx-cursor: hand; -fx-border-color: #3A3D46;");
            expandBtn.setTooltip(new Tooltip("Mở ảnh/video đã gửi"));
            StackPane.setAlignment(expandBtn, Pos.CENTER_RIGHT);
            contentArea.getChildren().add(expandBtn);

            expandBtn.setOnAction(ev -> {
                contentArea.getChildren().remove(expandBtn);
                rightPanelExpanded = true;
                panel.setPrefWidth(260);
                panel.setMaxWidth(260);
                panel.setVisible(true);
                panel.setManaged(true);
            });
        }
    }

    // ==================== Message Action Menus (từ Main1) ====================

    private void buildMessageActionMenus() {
        messageFullMenu = new ContextMenu();
        messageFullMenu.setStyle(
                "-fx-background-color: #25272D; -fx-border-color: #3A3D46; -fx-background-radius: 8;");

        MenuItem copyItem = new MenuItem("📋 Copy tin nhắn");
        copyItem.setStyle("-fx-text-fill: #E1E3E6;");
        copyItem.setOnAction(e -> showAlert("Hệ thống", "Đã sao chép tin nhắn vào Clipboard."));

        MenuItem pinItem = new MenuItem("⭐ Đánh dấu tin nhắn");
        pinItem.setStyle("-fx-text-fill: #E1E3E6;");

        MenuItem multiSelectItem = new MenuItem("📝 Chọn nhiều tin nhắn");
        multiSelectItem.setStyle("-fx-text-fill: #E1E3E6;");

        Menu moreOptions = new Menu("⚙️ Tùy chọn khác");
        moreOptions.setStyle("-fx-text-fill: #E1E3E6;");
        MenuItem forwardItem = new MenuItem("Chuyển tiếp...");
        forwardItem.setOnAction(e -> showAlert("Chuyển tiếp", "Tính năng đang được xử lý"));
        moreOptions.getItems().add(forwardItem);

        MenuItem unsendItem = new MenuItem("🔄 Thu hồi");
        unsendItem.setStyle("-fx-text-fill: #FFCC00;");
        unsendItem.setOnAction(e -> {
            if (currentSelectedMsgId != -1) {
                JsonObject req = new JsonObject();
                req.addProperty("messageId", currentSelectedMsgId);
                client.send(Protocol.TYPE_UNSEND, req);
            }
        });

        MenuItem deleteItem = new MenuItem("🗑️ Xóa (Chỉ phía tôi)");
        deleteItem.setStyle("-fx-text-fill: #FF453A; -fx-font-weight: bold;");

        messageFullMenu.getItems().addAll(
                copyItem, pinItem, multiSelectItem, moreOptions,
                new SeparatorMenuItem(), unsendItem, deleteItem);
    }

    public void triggerMessageActions(javafx.scene.Node msgNode, long messageId,
            double screenX, double screenY) {
        this.currentSelectedMsgId = messageId;
        messageFullMenu.show(msgNode, screenX, screenY);
    }

    // ==================== Navigation helpers ====================

    private void switchContentView(javafx.scene.Node view) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(view);
    }

    // ==================== ChatTab.Callback ====================

    // ==================== Call state ====================
    private boolean inCall = false;
    private boolean isVideoCall = false;
    private String callSessionId;
    private long callTargetId;
    private String callTargetName;
    private Stage callStage;
    private volatile long callStartTime;

    @Override
    public void onVoiceCall(long targetId) {
        String targetName = "";
        if (chatTab != null) {
            ChatTab.ConvItem c = chatTab.getActive();
            if (c != null)
                targetName = c.name;
        }
        showActiveCallWindow(targetId, targetName, false);
    }

    @Override
    public void onVideoCall(long targetId) {
        String targetName = "";
        if (chatTab != null) {
            ChatTab.ConvItem c = chatTab.getActive();
            if (c != null) targetName = c.name;
        }
        showActiveCallWindow(targetId, targetName, true);
    }

    @Override
    public void onSearch(String keyword) {
        // Global search — giữ nguyên từ MainView gốc
        if (keyword.isEmpty())
            return;
        try {
            long id = Long.parseLong(keyword);
            JsonObject data = new JsonObject();
            data.addProperty("keyword", String.valueOf(id));
            client.send(Protocol.TYPE_SEARCH_USER, data);
            return;
        } catch (NumberFormatException ignored) {
        }
        JsonObject data = new JsonObject();
        data.addProperty("keyword", keyword);
        client.send(Protocol.TYPE_SEARCH_USER, data);
    }

    @Override
    public void onLogout() {
        client.logout();
    }

    @Override
    public VoiceCallHandler getVoiceCallHandler() {
        return voiceCallHandler;
    }

    @Override
    public VideoStreamHandler getVideoCallHandler() {
        return videoStreamHandler;
    }

    @Override
    public void setVoiceCallHandler(VoiceCallHandler h) {
        this.voiceCallHandler = h;
    }

    @Override
    public void setVideoCallHandler(VideoStreamHandler h) {
        this.videoStreamHandler = h;
    }

    @Override
    public void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    // ==================== ContactsTab.Callback ====================

    @Override
    public void onStartChat(long userId, String displayName) {
        chatTab.addConv(displayName, userId, false, false);
        chatTab.highlightConversation(userId);
        switchContentView(chatView);
        chatNavBtn.setSelected(true);
    }

    // ==================== Server Message Routing (từ MainView gốc)
    // ====================

    public void onServerMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";

        switch (type) {
            case Protocol.TYPE_MESSAGE:
            case Protocol.TYPE_TYPING:
            case Protocol.TYPE_MESSAGE_STATUS:
            case Protocol.TYPE_UNSEND:
            case Protocol.TYPE_REACTION:
                chatTab.handleServerMessage(message);
                contactsTab.handleServerMessage(message);
                break;

            case Protocol.TYPE_SUCCESS:
                // Kiểm tra có phải kết quả search không
                if (message.has("data") && message.get("data").isJsonObject()) {
                    JsonObject d = message.getAsJsonObject("data");
                    if (d.has("results")) {
                        handleSearchResult(message);
                        break;
                    }
                }
                chatTab.handleServerMessage(message);
                contactsTab.handleServerMessage(message);
                break;

            case Protocol.TYPE_SEARCH_USER:
                handleSearchResult(message);
                break;

            case Protocol.TYPE_GROUP_MESSAGE:
                chatTab.handleServerMessage(message);
                contactsTab.handleServerMessage(message);
                break;

            case Protocol.TYPE_PRESENCE:
                handlePresence(message);
                break;

            case Protocol.TYPE_FRIEND_REQUEST:
            case Protocol.TYPE_FRIEND_RESPONSE:
                contactsTab.handleServerMessage(message);
                break;

            case Protocol.TYPE_VOICE_CALL_START:
            case Protocol.TYPE_VIDEO_CALL_START:
                handleIncomingCall(message);
                break;

            case Protocol.TYPE_VOICE_CALL_ACCEPT:
            case Protocol.TYPE_VIDEO_CALL_ACCEPT:
                handleCallAccepted(message);
                break;

            case Protocol.TYPE_VOICE_CALL_REJECT:
            case Protocol.TYPE_VIDEO_CALL_REJECT:
                handleCallRejected(message);
                break;

            case Protocol.TYPE_VOICE_CALL_END:
            case Protocol.TYPE_VIDEO_CALL_END:
                handleCallEnded(message);
                break;

            case Protocol.TYPE_RELAY_INFO:
                handleRelayInfo(message);
                break;
            
            case "VIDEO_FRAME":
                if (videoStreamHandler != null && message.has("frame")) {
                    videoStreamHandler.onFrame(message.get("frame").getAsString());
                }
                break;

            default:
                chatTab.handleServerMessage(message);
                contactsTab.handleServerMessage(message);
                break;
        }

        // Cập nhật badge lời mời kết bạn
        Platform.runLater(() -> {
            int pendingCount = contactsTab.getPendingCount();
            if (pendingCount > 0) {
                contactsBadge.setText(String.valueOf(pendingCount));
                contactsBadge.setVisible(true);
            } else {
                contactsBadge.setVisible(false);
            }
        });
    }

    private void handleSearchResult(JsonObject message) {
        JsonObject data = message.has("data") ? message.getAsJsonObject("data") : null;
        if (data == null || !data.has("results"))
            return;

        JsonArray results = data.getAsJsonArray("results");
        List<JsonObject> users = new ArrayList<>();
        for (JsonElement e : results) {
            users.add(e.getAsJsonObject());
        }
        chatTab.showSearchResults(users);
    }

    private void handlePresence(JsonObject message) {
        JsonObject data = message.has("data") ? message.getAsJsonObject("data") : null;
        if (data == null)
            return;
        long userId = data.has("userId") ? data.get("userId").getAsLong() : -1;
        String presence = data.has("presence") ? data.get("presence").getAsString() : "OFFLINE";
        if (userId > 0) {
            chatTab.updatePresence(userId, "ONLINE".equals(presence));
        }
    }

    // ==================== Call UI ====================

    /** Cửa sổ cuộc gọi đi (đang đổ chuông) */
    private void showOutgoingCallWindow(boolean isVideo) {
        Platform.runLater(() -> {
            closeCallStage();
            callStage = new Stage();
            callStage.setTitle(isVideo ? "MojiMoji - Gọi video" : "MojiMoji - Gọi thoại");
            callStage.initOwner(client.getPrimaryStage());

            VBox root = new VBox(20);
            root.setPadding(new Insets(30));
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #1C1E26;");

            Label icon = new Label(isVideo ? "\uD83D\uDCF9" : "\uD83D\uDCDE");
            icon.setFont(Font.font(48));
            Label name = new Label(callTargetName);
            name.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
            name.setTextFill(Color.WHITE);
            Label status = new Label("\u0110ang g\u1ECDi...");
            status.setFont(Font.font("SansSerif", 14));
            status.setTextFill(Color.rgb(140, 146, 172));

            Button endBtn = new Button("\u274C K\u1EBFt th\u00FAc");
            endBtn.setStyle("-fx-background-color: #E5484D; -fx-text-fill: white; -fx-background-radius: 25; -fx-padding: 12 30; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
            endBtn.setOnAction(e -> endCall());

            root.getChildren().addAll(icon, name, status, endBtn);
            callStage.setScene(new Scene(root, 320, 400));
            callStage.setOnCloseRequest(e -> endCall());
            callStage.show();
        });
    }

    /** Cửa sổ cuộc gọi đến */
    private void showIncomingCallWindow(long fromUserId, String fromName, boolean isVideo) {
        Platform.runLater(() -> {
            closeCallStage();
            callStage = new Stage();
            callStage.setTitle(isVideo ? "Cu\u1ED9c g\u1ECDi video \u0111\u1EBFn" : "Cu\u1ED9c g\u1ECDi tho\u1EA1i \u0111\u1EBFn");

            VBox root = new VBox(20);
            root.setPadding(new Insets(30));
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #1C1E26;");

            Label icon = new Label(isVideo ? "\uD83D\uDCF9" : "\uD83D\uDCDE");
            icon.setFont(Font.font(48));
            Label title = new Label(isVideo ? "Cu\u1ED9c g\u1ECDi video" : "Cu\u1ED9c g\u1ECDi tho\u1EA1i");
            title.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
            title.setTextFill(Color.WHITE);
            Label name = new Label(fromName);
            name.setFont(Font.font("SansSerif", FontWeight.BOLD, 22));
            name.setTextFill(Color.WHITE);

            HBox btnBox = new HBox(30);
            btnBox.setAlignment(Pos.CENTER);

            Button acceptBtn = new Button("\u2705 Ch\u1EA5p nh\u1EADn");
            acceptBtn.setStyle("-fx-background-color: #31D158; -fx-text-fill: white; -fx-background-radius: 25; -fx-padding: 12 24; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
            acceptBtn.setOnAction(e -> {
                JsonObject data = new JsonObject();
                data.addProperty("targetId", fromUserId);
                data.addProperty("sessionId", callSessionId != null ? callSessionId : "");
                client.send(isVideo ? Protocol.TYPE_VIDEO_CALL_ACCEPT : Protocol.TYPE_VOICE_CALL_ACCEPT, data);
                showActiveCallWindow(fromUserId, fromName, isVideo);
            });

            Button rejectBtn = new Button("\u274C T\u1EEB ch\u1ED1i");
            rejectBtn.setStyle("-fx-background-color: #E5484D; -fx-text-fill: white; -fx-background-radius: 25; -fx-padding: 12 24; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
            rejectBtn.setOnAction(e -> {
                JsonObject data = new JsonObject();
                data.addProperty("targetId", fromUserId);
                client.send(isVideo ? Protocol.TYPE_VIDEO_CALL_REJECT : Protocol.TYPE_VOICE_CALL_REJECT, data);
                closeCallStage();
            });

            btnBox.getChildren().addAll(rejectBtn, acceptBtn);
            root.getChildren().addAll(icon, title, name, btnBox);
            callStage.setScene(new Scene(root, 320, 400));
            callStage.setOnCloseRequest(e -> {
                JsonObject data = new JsonObject();
                data.addProperty("targetId", fromUserId);
                client.send(isVideo ? Protocol.TYPE_VIDEO_CALL_REJECT : Protocol.TYPE_VOICE_CALL_REJECT, data);
            });
            callStage.show();
        });
    }

    /** Cửa sổ cuộc gọi đang hoạt động (timer + mute + end) */
    private void showActiveCallWindow(long targetId, String targetName, boolean isVideo) {
        this.callTargetId = targetId;
        this.callTargetName = targetName;
        this.isVideoCall = isVideo;
        Platform.runLater(() -> {
            closeCallStage();

            // Khởi tạo video stream handler nếu là video call
            if (isVideo) {
                remoteVideoView = new ImageView();
                remoteVideoView.setFitWidth(400);
                remoteVideoView.setFitHeight(300);
                remoteVideoView.setPreserveRatio(true);
                localVideoView = new ImageView();
                localVideoView.setFitWidth(120);
                localVideoView.setFitHeight(90);
                localVideoView.setPreserveRatio(true);

                videoStreamHandler = new VideoStreamHandler();
                videoStreamHandler.setClient(client);
                videoStreamHandler.setViews(remoteVideoView, localVideoView);
                videoStreamHandler.setStateListener(state -> {
                    Platform.runLater(() -> {
                        if ("ended".equals(state)) {
                            inCall = false;
                            closeCallStage();
                        }
                    });
                });
                videoStreamHandler.setErrorListener(msg -> {
                    Platform.runLater(() -> showAlert("Video Call Error", msg));
                });
                videoStreamHandler.start(callTargetId, false);
            }

            callStage = new Stage();
            callStage.setTitle("MojiMoji - Đang gọi");
            callStartTime = System.currentTimeMillis();

            VBox root = new VBox(20);
            root.setPadding(new Insets(30));
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: #1C1E26;");

            if (isVideo) {
                VBox videoBox = new VBox(10);
                videoBox.setAlignment(Pos.CENTER);
                StackPane remotePane = new StackPane(remoteVideoView);
                remotePane.setStyle("-fx-background-color: black; -fx-background-radius: 8;");
                StackPane localPane = new StackPane(localVideoView);
                localPane.setStyle("-fx-background-color: black; -fx-background-radius: 8;");
                localPane.setMaxSize(120, 90);
                StackPane.setAlignment(localPane, Pos.BOTTOM_RIGHT);
                StackPane videoStack = new StackPane(remotePane, localPane);
                videoBox.getChildren().add(videoStack);
                root.getChildren().add(videoBox);
            }

            Label icon = new Label(isVideoCall ? "\uD83D\uDCF9" : "\uD83D\uDCDE");
            icon.setFont(Font.font(48));
            Label name = new Label(callTargetName);
            name.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
            name.setTextFill(Color.WHITE);

            Label timerLbl = new Label("00:00");
            timerLbl.setFont(Font.font("SansSerif", 14));
            timerLbl.setTextFill(Color.rgb(140, 146, 172));

            // Timer thread
            Thread timerThread = new Thread(() -> {
                while (inCall && callStage != null && callStage.isShowing()) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    long elapsed = System.currentTimeMillis() - callStartTime;
                    long sec = elapsed / 1000;
                    Platform.runLater(() -> timerLbl.setText(
                            String.format("%02d:%02d", sec / 60, sec % 60)));
                }
            }, "call-timer");
            timerThread.setDaemon(true);
            timerThread.start();

            HBox ctrlBox = new HBox(20);
            ctrlBox.setAlignment(Pos.CENTER);

            Button muteBtn = new Button("\uD83D\uDD07");
            muteBtn.setStyle("-fx-background-color: #3A3D52; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 50; -fx-min-height: 50; -fx-font-size: 20px; -fx-cursor: hand;");
            muteBtn.setOnAction(e -> {
                boolean muted = !muteBtn.getText().contains("\uD83D\uDD0A");
                muteBtn.setText(muted ? "\uD83D\uDD07" : "\uD83D\uDD0A");
                if (voiceCallHandler != null) voiceCallHandler.setMuted(muted);
            });

            Button endBtn = new Button("\uD83D\uDCDE");
            endBtn.setStyle("-fx-background-color: #E5484D; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 56; -fx-min-height: 56; -fx-font-size: 24px; -fx-cursor: hand;");
            endBtn.setOnAction(e -> endCall());

            Button speakerBtn = new Button("\uD83D\uDD0A");
            speakerBtn.setStyle("-fx-background-color: #3A3D52; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 50; -fx-min-height: 50; -fx-font-size: 20px; -fx-cursor: hand;");

            ctrlBox.getChildren().addAll(muteBtn, endBtn, speakerBtn);
            root.getChildren().addAll(icon, name, timerLbl, ctrlBox);
            callStage.setScene(new Scene(root, isVideo ? 500 : 320, isVideo ? 600 : 420));
            callStage.setOnCloseRequest(e -> endCall());
            callStage.show();
        });
    }

    private void closeCallStage() {
        if (callStage != null && callStage.isShowing()) {
            callStage.close();
            callStage = null;
        }
    }

    /** K\u1EBFt th\u00FAc cu\u1ED9c g\u1ECDi */
    private void endCall() {
        if (!inCall) return;
        inCall = false;
        if (voiceCallHandler != null && voiceCallHandler.isRunning()) voiceCallHandler.endCall();
        if (videoStreamHandler != null && videoStreamHandler.isRunning()) videoStreamHandler.end();
        JsonObject data = new JsonObject();
        data.addProperty("targetId", callTargetId);
        if (callSessionId != null) data.addProperty("sessionId", callSessionId);
        client.send(isVideoCall ? Protocol.TYPE_VIDEO_CALL_END : Protocol.TYPE_VOICE_CALL_END, data);
        closeCallStage();
    }

    /** Nh\u1EADn cu\u1ED9c g\u1ECDi \u0111\u1EBFn */
    private void handleIncomingCall(JsonObject message) {
        long fromId = message.has("senderId") ? message.get("senderId").getAsLong() : -1;
        String fromName = message.has("senderName") ? message.get("senderName").getAsString() : "\u1EA8n danh";
        boolean isVideo = message.get("type").getAsString().contains("VIDEO");
        inCall = true;
        isVideoCall = isVideo;
        callTargetId = fromId;
        callTargetName = fromName;
        showIncomingCallWindow(fromId, fromName, isVideo);
    }

    /** Cu\u1ED9c g\u1ECDi \u0111\u01B0\u1EE3c ch\u1EA5p nh\u1EADn */
    private void handleCallAccepted(JsonObject message) {
        // Server sẽ gửi RELAY_INFO riêng, chờ RELAY_INFO để kết nối
        showActiveCallWindow(callTargetId, callTargetName, isVideoCall);
    }

    /** Cu\u1ED9c g\u1ECDi b\u1ECB t\u1EEB ch\u1ED1i */
    private void handleCallRejected(JsonObject message) {
        Platform.runLater(() -> {
            showAlert("Cu\u1ED9c g\u1ECDi", callTargetName + " \u0111\u00E3 t\u1EEB ch\u1ED1i cu\u1ED9c g\u1ECDi.");
            closeCallStage();
            inCall = false;
        });
    }

    /** Cu\u1ED9c g\u1ECDi k\u1EBFt th\u00FAc t\u1EEB ph\u00EDa kia */
    private void handleCallEnded(JsonObject message) {
        Platform.runLater(() -> {
            String name = message.has("senderName") ? message.get("senderName").getAsString() : "\u1EA8n danh";
            showAlert("K\u1EBFt th\u00FAc", name + " \u0111\u00E3 k\u1EBFt th\u00FAc cu\u1ED9c g\u1ECDi.");
            closeCallStage();
            inCall = false;
        });
    }

    /** Nh\u1EADn th\u00F4ng tin relay t\u1EEB server -> k\u1EBFt n\u1ED1i UDP */
    private void handleRelayInfo(JsonObject message) {
        String sessionId = message.has("sessionId") ? message.get("sessionId").getAsString() : "";
        int voicePort = message.has("voicePort") ? message.get("voicePort").getAsInt() : 9001;
        int videoPort = message.has("videoPort") ? message.get("videoPort").getAsInt() : 9002;
        boolean isVideo = message.has("isVideo") && message.get("isVideo").getAsBoolean();
        boolean isCaller = message.has("caller") && message.get("caller").getAsBoolean();
        callSessionId = sessionId;

        String relayHost = "127.0.0.1"; // server c\u0169ng l\u00E0 relay trong local dev
        int relayPort = isVideo ? videoPort : voicePort;

        // Kết nối call handler tới relay
        Platform.runLater(() -> {
            // Voice call relay
            voiceCallHandler = new VoiceCallHandler();
            voiceCallHandler.prepareForCall();
            voiceCallHandler.startCall(sessionId, relayHost, relayPort);
        });
    }

    private void showVideoSettings() {
        if (videoStreamHandler == null)
            return;
        List<String> cams = VideoStreamHandler.listCameras();
        if (cams.isEmpty()) {
            showAlert("Camera", "Khong tim thay camera.");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(cams.get(0), cams);
        dialog.setTitle("Cai dat camera");
        dialog.setHeaderText("Chon camera");
        dialog.setContentText("Camera:");
        dialog.showAndWait().ifPresent(selected -> {
            int idx = cams.indexOf(selected);
            if (idx >= 0) {
                videoStreamHandler.selectCamera(idx);
                showAlert("Camera", "Da chon: " + selected + "\nVui long bat lai cuoc goi de ap dung.");
            }
        });
    }

    public void onDisconnected() {
        if (voiceCallHandler != null && voiceCallHandler.isRunning())
            voiceCallHandler.endCall();
        if (videoStreamHandler != null && videoStreamHandler.isRunning())
            videoStreamHandler.end();
    }

    // ==================== Getters ====================

    public ChatTab getChatTab() {
        return chatTab;
    }

    public ContactsTab getContactsTab() {
        return contactsTab;
    }

    public ProfileTab getProfileTab() {
        return profileTab;
    }
}
