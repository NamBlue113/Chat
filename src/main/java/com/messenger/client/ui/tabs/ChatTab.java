package com.messenger.client.ui.tabs;

import com.google.gson.*;
import com.messenger.client.ChatClient;
import com.messenger.client.ChatHistoryManager;
import com.messenger.client.call.VoiceCallHandler;
import com.messenger.client.call.VideoStreamHandler;
import com.messenger.client.ui.IconManager;
import com.messenger.client.ui.StickerManager;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tab 1: Tin nhắn — danh sách cuộc trò chuyện + khu vực chat.
 */
public class ChatTab {

    private final ChatClient client;
    private final User currentUser;
    private final Gson gson = new Gson();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");

    // --- Conversation list ---
    public static class ConvItem {
        public String name, initials, lastMsg, time;
        public String avatarUrl;          // actual avatar image URL
        public Color avatarColor;
        public boolean online;
        public int unread;
        public long uid;
        public boolean isGroup;
        public ConvItem(String name, long uid, boolean online, boolean isGroup) {
            this.name = name; this.uid = uid; this.online = online; this.isGroup = isGroup;
            this.initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.substring(0, 1).toUpperCase();
            this.avatarColor = COLORS[(int)(Math.abs(uid) % COLORS.length)];
        }
    }
    private static final Color[] COLORS = { Color.rgb(124,77,255),Color.rgb(233,30,140),Color.rgb(255,109,0),
            Color.rgb(0,137,123),Color.rgb(244,81,30),Color.rgb(0,132,255),Color.rgb(76,175,80),Color.rgb(156,39,176) };

    public static final String[][] EMOJI_GRID = {
        {"\uD83D\uDE00","\uD83D\uDE02","\uD83D\uDE0D","\uD83D\uDE0E","\uD83D\uDE09","\uD83D\uDE18","\uD83D\uDE1C","\uD83D\uDE1D"},
        {"\uD83D\uDE22","\uD83D\uDE2D","\uD83D\uDE21","\uD83D\uDE20","\uD83D\uDE2E","\uD83D\uDE31","\uD83D\uDE2F","\uD83D\uDE33"},
        {"\uD83D\uDC4D","\uD83D\uDC4E","\uD83D\uDC4F","\uD83D\uDCAA","\u270C\uFE0F","\uD83E\uDD1D","\uD83D\uDE4F","\uD83D\uDC8B"},
        {"\u2764\uFE0F","\uD83D\uDC9B","\uD83D\uDC9A","\uD83D\uDC99","\uD83D\uDC9C","\uD83D\uDC96","\uD83D\uDC97","\uD83D\uDC94"},
        {"\uD83D\uDD25","\u2B50","\uD83C\uDF1F","\uD83D\uDCAF","\uD83C\uDF89","\uD83C\uDF38","\uD83C\uDF3A","\uD83C\uDF3B"},
        {"\uD83D\uDC36","\uD83D\uDC31","\uD83D\uDC3B","\uD83E\uDD81","\uD83D\uDC30","\uD83D\uDC39","\uD83D\uDC3C","\uD83D\uDC28"},
        {"\uD83C\uDF54","\uD83C\uDF55","\uD83C\uDF5C","\uD83C\uDF64","\uD83C\uDF66","\uD83D\uDC7B","\uD83D\uDC7D","\uD83D\uDC7E"},
        {"\u231B","\uD83D\uDCA1","\uD83D\uDCC8","\uD83C\uDF0D","\uD83D\uDE80","\uD83C\uDFC6","\uD83C\uDFB5","\uD83D\uDCF7"}
    };

    private ListView<ConvItem> listView;
    private final ObservableList<ConvItem> listItems = FXCollections.observableArrayList();
    private VBox msgArea;
    private ScrollPane msgScroll;
    private VBox chatAreaVBox;
    private TextField input;
    private Label typingLbl, hdrName, hdrStatus;
    private StackPane hdrAvatar;
    private ConvItem active;
    private final Map<Long,ConvItem> convMap = new HashMap<>();
    private Popup emojiPopup;
    private Button emojiBtn;
    private static final double SIDEBAR_MIN_WIDTH = 220;
    private static final double SIDEBAR_PREF_WIDTH = 310;
    private static final double SIDEBAR_MAX_WIDTH = 360;

    // Search
    private TextField searchField;
    private HBox searchWrap;
    private ListView<String> searchResultView;
    private final ObservableList<String> searchItems = FXCollections.observableArrayList();
    private final Map<Integer, JsonObject> searchIndex = new HashMap<>();
    private Timer searchTimer;
    private Popup searchPopup;

    // Call handlers owned by parent MainView
    private VoiceCallHandler voiceCallHandler;
    private VideoStreamHandler videoStreamHandler;

    // Callbacks to parent
    public interface Callback {
        void onVoiceCall(long targetId);
        void onVideoCall(long targetId);
        void onSearch(String keyword);
        void onLogout();
        void onThemeToggle();
        VoiceCallHandler getVoiceCallHandler();
        VideoStreamHandler getVideoCallHandler();
        void setVoiceCallHandler(VoiceCallHandler h);
        void setVideoCallHandler(VideoStreamHandler h);
        void showAlert(String title, String msg);
    }
    private final Callback callback;

    public ChatTab(ChatClient client, User currentUser, Callback callback) {
        this.client = client;
        this.currentUser = currentUser;
        this.callback = callback;
    }

    // --- Public API ---
    public ConvItem getActive() { return active; }
    public ConvItem addConv(String name, long uid, boolean online, boolean grp) {
        if (convMap.containsKey(uid)) return convMap.get(uid);
        ConvItem c = new ConvItem(name, uid, online, grp);
        convMap.put(uid, c);
        listItems.add(0, c);
        return c;
    }
    public void removeConv(long uid) {
        ConvItem c = convMap.remove(uid);
        if (c != null) {
            listItems.remove(c);
            if (active != null && active.uid == uid) { active = null; hdrName.setText("Messenger"); hdrStatus.setText(""); msgArea.getChildren().clear(); }
        }
    }
    public void renameConv(long uid, String newName) {
        ConvItem c = convMap.get(uid);
        if (c != null) { c.name = newName; c.initials = newName.length()>=2?newName.substring(0,2).toUpperCase():newName.substring(0,1).toUpperCase(); listView.refresh(); if(active!=null&&active.uid==uid) hdrName.setText(newName); }
    }
    public void pinConv(long uid) {
        ConvItem c = convMap.get(uid);
        if (c != null) { listItems.remove(c); listItems.add(0, c); listView.refresh(); }
    }
    public void updatePresence(long uid, boolean online) {
        ConvItem c = convMap.get(uid);
        if (c == null) return;
        Platform.runLater(() -> {
            c.online = online;
            listView.refresh();
            // Đồng bộ header nếu đang mở conversation này
            if (active != null && active.uid == uid) {
                if (online) {
                    hdrStatus.setText("● Đang hoạt động");
                    hdrStatus.setStyle("-fx-text-fill:#31D158; -fx-font-size: 12px;");
                } else {
                    hdrStatus.setText("Không hoạt động");
                    hdrStatus.setStyle("-fx-text-fill:#65676B; -fx-font-size: 12px;");
                }
                hdrAvatar.getChildren().clear();
                hdrAvatar.getChildren().add(makeAvatar(c.initials, c.avatarColor, 38, online, c.avatarUrl));
            }
        });
    }
    public void highlightConversation(long uid) {
        ConvItem c = convMap.get(uid);
        if (c != null) { listView.getSelectionModel().select(c); listView.scrollTo(c); }
    }
    public void refreshConvList() { listView.refresh(); }

    // Nodes được tạo 1 lần, dùng cho layout Messenger
    private javafx.scene.Node sidebarNode;
    private javafx.scene.Node chatAreaNode;

    // --- Build UI ---
    public javafx.scene.Node build() {
        if (sidebarNode == null) sidebarNode = buildSidebar();
        if (chatAreaNode == null) chatAreaNode = buildChatArea();
        buildEmojiPopup();
        BorderPane root = new BorderPane();
        root.getStyleClass().add("chat-area");
        root.setLeft(sidebarNode);
        root.setCenter(chatAreaNode);
        return root;
    }

    /** Lấy node sidebar (dùng cho layout Messenger) */
    public javafx.scene.Node getSidebarNode() {
        if (sidebarNode == null) sidebarNode = buildSidebar();
        return sidebarNode;
    }

    /** Lấy node khu vực chat (header + messages + input - dùng cho layout Messenger) */
    public javafx.scene.Node getChatAreaNode() {
        if (chatAreaNode == null) {
            chatAreaNode = buildChatArea();
            buildEmojiPopup();
        }
        return chatAreaNode;
    }

    private javafx.scene.Node buildSidebar() {
        VBox sb = new VBox(0);
        sb.getStyleClass().add("sidebar");
        sb.setMinWidth(SIDEBAR_MIN_WIDTH);
        sb.setPrefWidth(SIDEBAR_PREF_WIDTH);
        sb.setMaxWidth(SIDEBAR_MAX_WIDTH);

        VBox top = new VBox(0);
        top.setPadding(new Insets(14,14,8,14)); top.getStyleClass().add("sidebar-top");
        Label t = new Label("\u0110o\u1EA1n chat"); t.setFont(Font.font("SansSerif",FontWeight.BOLD,22)); t.getStyleClass().add("sidebar-title");
        HBox hdr = new HBox(8); hdr.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(t, Priority.ALWAYS);
        Button gb = new Button("+ Nh\u00F3m m\u1EDBi"); gb.getStyleClass().add("btn-group-new");
        gb.setMinWidth(Region.USE_PREF_SIZE);
        gb.setOnAction(e -> showCreateGroup());
        setTT(gb, "T\u1EA1o nh\u00F3m chat m\u1EDBi");
        hdr.getChildren().addAll(t, gb);

        searchField = new TextField();
        searchField.setPromptText("T\u00ECm ki\u1EBFm ng\u01B0\u1EDDi d\u00F9ng...");
        searchField.getStyleClass().add("sidebar-search");
        // Debounced search: gõ → 300ms → gửi request
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (searchTimer != null) searchTimer.cancel();
            String keyword = newVal.trim();
            if (keyword.isEmpty()) {
                hideSearchResults();
                return;
            }
            searchTimer = new Timer("search-debounce", true);
            searchTimer.schedule(new TimerTask() {
                @Override public void run() {
                    Platform.runLater(() -> callback.onSearch(keyword));
                }
            }, 300);
        });
        searchField.setOnAction(e -> {
            String keyword = searchField.getText().trim();
            if (!keyword.isEmpty()) callback.onSearch(keyword);
        });

        // Dropdown kết quả tìm kiếm — cell có avatar + tên + trạng thái
        searchResultView = new ListView<>(searchItems);
        searchResultView.setPrefHeight(200);
        searchResultView.setMaxHeight(280);
        searchResultView.setStyle(
            "-fx-background-color: #25272D; -fx-background-radius: 8;" +
            "-fx-border-color: #3A3D46; -fx-border-width: 1; -fx-border-radius: 8;");
        searchResultView.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                JsonObject user = searchIndex.get(getIndex());
                if (user == null) { setText(item); return; }
                String nm = user.has("displayName") ? user.get("displayName").getAsString()
                          : user.get("username").getAsString();
                String un = user.has("username") ? user.get("username").getAsString() : "";
                boolean isOn = "ONLINE".equals(user.has("presence") ? user.get("presence").getAsString() : "");
                String ini = nm.length() >= 2 ? nm.substring(0,2).toUpperCase() : nm.substring(0,1).toUpperCase();
                javafx.scene.shape.Circle avBg = new javafx.scene.shape.Circle(18, javafx.scene.paint.Color.rgb(0, 132, 255));
                Label avLbl = new Label(ini);
                avLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
                avLbl.setTextFill(javafx.scene.paint.Color.WHITE);
                StackPane avBase = new StackPane(avBg, avLbl);
                avBase.setMinSize(36, 36); avBase.setMaxSize(36, 36);
                javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5,
                    isOn ? javafx.scene.paint.Color.rgb(49, 209, 88)
                         : javafx.scene.paint.Color.rgb(100, 100, 110));
                StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
                StackPane av = new StackPane(avBase, dot);
                av.setMinSize(36, 36); av.setMaxSize(36, 36);
                Label nameLbl = new Label(nm);
                nameLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
                nameLbl.setTextFill(javafx.scene.paint.Color.WHITE);
                Label userLbl = new Label("@" + un);
                userLbl.setFont(Font.font("SansSerif", 11));
                userLbl.setTextFill(javafx.scene.paint.Color.rgb(120, 120, 140));
                VBox info = new VBox(2, nameLbl, userLbl);
                HBox row = new HBox(10, av, info);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 8, 4, 8));
                setGraphic(row); setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            }
        });
        searchResultView.setOnMouseClicked(ev -> {
            int idx = searchResultView.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && searchIndex.containsKey(idx)) {
                showSearchUserActions(searchIndex.get(idx));
            }
        });

        searchPopup = new Popup();
        searchPopup.setAutoHide(true);
        searchPopup.getContent().add(searchResultView);

        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.getStyleClass().add("search-icon");
        searchWrap = new HBox(8, searchIcon, searchField);
        searchWrap.setAlignment(Pos.CENTER_LEFT);
        searchWrap.getStyleClass().add("search-wrapper");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchWrap.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (searchResultView != null) searchResultView.setPrefWidth(newVal.doubleValue());
        });

        top.getChildren().addAll(hdr, searchWrap);

        listView = new ListView<>(listItems);
        listView.setCellFactory(lv -> new ConvCell());
        listView.getSelectionModel().selectedItemProperty().addListener((o,ov,nv)->{ if(nv!=null) open(nv); });
        VBox.setVgrow(listView,Priority.ALWAYS);
        sb.getChildren().addAll(top, listView);

        // User bar at bottom
        HBox ub = new HBox(10); ub.setPadding(new Insets(10,14,10,14)); ub.setAlignment(Pos.CENTER_LEFT); ub.getStyleClass().add("user-bar");
        String in = currentUser.getDisplayName().length()>=2 ? currentUser.getDisplayName().substring(0,2).toUpperCase() : currentUser.getDisplayName().substring(0,1).toUpperCase();
        ub.getChildren().add(makeAvatar(in,Color.rgb(0,132,255),36,true,currentUser.getAvatarUrl()));
        Label un = new Label(currentUser.getDisplayName()); un.setFont(Font.font("SansSerif",FontWeight.BOLD,13)); un.getStyleClass().add("user-bar-name");
        HBox.setHgrow(un,Priority.ALWAYS); ub.getChildren().add(un);

        ToggleButton tg = new ToggleButton("\uD83C\uDF19"); tg.getStyleClass().add("btn-icon"); tg.setSelected(true);
        setTT(tg, "Chuy\u1EC3n ch\u1EBF \u0111\u1ED9 s\u00E1ng/t\u1ED1i");
        tg.setOnAction(e->{ tg.setText(tg.isSelected()?"\uD83C\uDF19":"\u2600\uFE0F"); callback.onThemeToggle(); });

        Button lo = new Button("\uD83D\uDEAA"); lo.getStyleClass().add("btn-icon");
        setTT(lo, "\u0110\u0103ng xu\u1EA5t");
        lo.setOnAction(e -> callback.onLogout());
        ub.getChildren().addAll(new HBox(6,tg,lo));
        sb.getChildren().add(ub);
        return sb;
    }

    private VBox buildChatArea() {
        VBox ca = new VBox(0);
        // Header
        HBox hd = new HBox(12); hd.setPadding(new Insets(10,16,10,16)); hd.setAlignment(Pos.CENTER_LEFT); hd.getStyleClass().add("chat-header");
        hdrAvatar = new StackPane();
        hdrAvatar.getChildren().add(avatar("M", Color.rgb(0,132,255), 38, false));
        hd.getChildren().add(hdrAvatar);
        VBox nb = new VBox(1);
        hdrName=new Label("Messenger"); hdrName.setFont(Font.font("SansSerif",FontWeight.BOLD,16)); hdrName.getStyleClass().add("chat-header-name");
        hdrStatus=new Label(""); hdrStatus.setFont(Font.font("SansSerif",12)); hdrStatus.getStyleClass().add("chat-header-status");
        nb.getChildren().addAll(hdrName,hdrStatus);
        HBox.setHgrow(nb,Priority.ALWAYS); hd.getChildren().add(nb);
        // Click vào header info → mở Group Info (nếu là group)
        nb.setCursor(javafx.scene.Cursor.HAND);
        nb.setOnMouseClicked(e -> {
            if (active != null && active.isGroup) {
                showGroupInfo(active.uid);
            }
        });
        hdrAvatar.setCursor(javafx.scene.Cursor.HAND);
        hdrAvatar.setOnMouseClicked(e -> {
            if (active != null && active.isGroup) {
                showGroupInfo(active.uid);
            }
        });

        Button voiceBtn = IconManager.createHeaderButton("voice-call.png", "\uD83D\uDCDE", "G\u1ECDi tho\u1EA1i");
        voiceBtn.setOnAction(e -> { if(active!=null&&!active.isGroup) callback.onVoiceCall(active.uid); });
        setTT(voiceBtn, "G\u1ECDi tho\u1EA1i");
        hd.getChildren().add(voiceBtn);

        Button videoBtn = IconManager.createHeaderButton("video-call.png", "\uD83D\uDCF9", "G\u1ECDi video");
        videoBtn.setOnAction(e -> { if(active!=null&&!active.isGroup) callback.onVideoCall(active.uid); });
        setTT(videoBtn, "G\u1ECDi video");
        hd.getChildren().add(videoBtn);
        ca.getChildren().add(hd);

        // Messages
        msgArea = new VBox(4); msgArea.setPadding(new Insets(10));
        msgArea.getChildren().add(buildChatEmptyState());
        msgScroll = new ScrollPane(msgArea); msgScroll.setFitToWidth(true); msgScroll.getStyleClass().add("chat-message-list");

        // N\u00FAt scroll xu\u1ED1ng cu\u1ED1i (C2)
        Button scrollDownBtn = new Button("\u2B07");
        scrollDownBtn.getStyleClass().add("scroll-down-btn");
        scrollDownBtn.setVisible(false);
        scrollDownBtn.setOnAction(e -> msgScroll.setVvalue(1.0));
        StackPane msgScrollWrapper = new StackPane(msgScroll, scrollDownBtn);
        StackPane.setAlignment(scrollDownBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(scrollDownBtn, new Insets(0, 24, 24, 0));
        msgScroll.vvalueProperty().addListener((obs, oldVal, newVal) ->
            scrollDownBtn.setVisible(newVal.doubleValue() < 0.92));
        VBox.setVgrow(msgScrollWrapper, Priority.ALWAYS);
        ca.getChildren().add(msgScrollWrapper);

        typingLbl = new Label(""); typingLbl.getStyleClass().add("typing-indicator"); typingLbl.setPadding(new Insets(2,15,2,15)); ca.getChildren().add(typingLbl);

        // Input bar
        HBox ib = new HBox(8); ib.setPadding(new Insets(10,14,10,14)); ib.setAlignment(Pos.CENTER_LEFT); ib.getStyleClass().add("input-bar");

        Button ab = IconManager.createInputButton("attach.png", "\uD83D\uDCCE", "G\u1EEDi file");
        ab.setOnAction(e->chooseFile()); setTT(ab,"G\u1EEDi file"); ib.getChildren().add(ab);

        Button pb = IconManager.createInputButton("image.png", "\uD83D\uDDBC", "G\u1EEDi \u1EA3nh");
        pb.setOnAction(e->chooseImage()); setTT(pb,"G\u1EEDi \u1EA3nh"); ib.getChildren().add(pb);

        emojiBtn = IconManager.createInputButton("emoji.png", "\uD83D\uDE00", "Bi\u1EC3u t\u01B0\u1EE3ng c\u1EA3m x\u00FAc");
        emojiBtn.setOnAction(e -> toggleEmojiPicker()); setTT(emojiBtn,"Ch\u1ECDn emoji"); ib.getChildren().add(emojiBtn);

        input = new TextField(); input.setPromptText("Aa"); input.getStyleClass().add("input-message"); HBox.setHgrow(input,Priority.ALWAYS);
        setTT(input,"Nh\u1EADp tin nh\u1EAFn...");
        input.setOnAction(e->send());
        input.textProperty().addListener((o,ov,nv)->{ if(active!=null&&!active.isGroup&&active.uid>0){ JsonObject d=new JsonObject(); d.addProperty("receiverId",active.uid); d.addProperty("typing",!nv.isEmpty()); client.send(Protocol.TYPE_TYPING,d); }});
        ib.getChildren().add(input);

        Button sb = new Button(); sb.getStyleClass().add("btn-send"); setTT(sb,"G\u1EEDi"); sb.setOnAction(e->send()); ib.getChildren().add(sb);
        ca.getChildren().add(ib);
        chatAreaVBox = ca;
        return ca;
    }

    // --- Emoji + Sticker picker ---
    private void buildEmojiPopup() {
        emojiPopup = new Popup(); emojiPopup.setAutoHide(true);

        TabPane pickerTabs = new TabPane();
        pickerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pickerTabs.setPrefWidth(310); pickerTabs.setMaxWidth(310);
        pickerTabs.setPrefHeight(320);
        pickerTabs.setStyle("-fx-background-color: #252840; -fx-background-radius: 10; -fx-border-color: #3A3D52; -fx-border-radius: 10;");

        // Tab 1: Emoji
        Tab emojiTab = new Tab("\uD83D\uDE00");
        FlowPane emojiGrid = new FlowPane(4, 4);
        emojiGrid.setPadding(new Insets(8));
        for (String[] row : EMOJI_GRID) for (String emoji : row) {
            Button eb = new Button(emoji); eb.setFont(Font.font(20)); eb.setMinSize(32,32); eb.setMaxSize(32,32);
            eb.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
            eb.setOnMouseEntered(e->eb.setStyle("-fx-background-color: #3A3D52; -fx-cursor: hand; -fx-padding: 0; -fx-background-radius: 6;"));
            eb.setOnMouseExited(e->eb.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;"));
            eb.setOnAction(e->{ insertEmoji(emoji); emojiPopup.hide(); });
            emojiGrid.getChildren().add(eb);
        }
        emojiTab.setContent(emojiGrid);

        pickerTabs.getTabs().add(emojiTab);

        // Sticker tabs (one per pack)
        for (com.messenger.client.ui.StickerManager.StickerPack pack : com.messenger.client.ui.StickerManager.getPacks()) {
            Tab stickerTab = new Tab(pack.icon + " " + pack.name);
            stickerTab.getStyleClass().add("sticker-tab");
            FlowPane stickerGrid = new FlowPane(6, 6);
            stickerGrid.setPadding(new Insets(10));
            stickerGrid.setPrefWrapLength(280);

            for (int i = 0; i < pack.stickers.length; i++) {
                final int idx = i;
                String[] sticker = pack.stickers[i];
                VBox stickerItem = new VBox(4);
                stickerItem.setAlignment(Pos.CENTER);
                stickerItem.setMinSize(60, 65);

                Label stickerEmoji = new Label(sticker[1]);
                stickerEmoji.setFont(Font.font(28));
                Label stickerName = new Label(sticker[0]);
                stickerName.setFont(Font.font(9));
                stickerName.setTextFill(Color.rgb(140, 146, 172));
                stickerItem.getChildren().addAll(stickerEmoji, stickerName);

                stickerItem.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;");
                stickerItem.setOnMouseEntered(e -> stickerItem.setStyle("-fx-background-color: #3A3D52; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;"));
                stickerItem.setOnMouseExited(e -> stickerItem.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;"));
                stickerItem.setOnMouseClicked(e -> {
                    sendSticker(pack.id, idx);
                    emojiPopup.hide();
                });

                stickerGrid.getChildren().add(stickerItem);
            }

            ScrollPane stickerScroll = new ScrollPane(stickerGrid);
            stickerScroll.setFitToWidth(true);
            stickerScroll.setStyle("-fx-background-color: transparent;");
            stickerTab.setContent(stickerScroll);
            pickerTabs.getTabs().add(stickerTab);
        }

        // Tab Icon — load PNG từ /icons/ classpath
        Tab iconTab = new Tab("🖼 Icon");
        iconTab.getStyleClass().add("sticker-tab");
        FlowPane iconGrid = new FlowPane(6, 6);
        iconGrid.setPadding(new Insets(10));
        iconGrid.setPrefWrapLength(280);

        for (String iconFile : ICON_FILES) {
            try {
                java.io.InputStream is = ChatTab.class.getResourceAsStream("/icons/" + iconFile);
                if (is == null) continue;
                javafx.scene.image.Image img = new javafx.scene.image.Image(is, 48, 48, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(48);
                iv.setFitHeight(48);
                iv.setPreserveRatio(true);

                StackPane iconItem = new StackPane(iv);
                iconItem.setMinSize(58, 58);
                iconItem.setMaxSize(58, 58);
                iconItem.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;");
                iconItem.setOnMouseEntered(e -> iconItem.setStyle("-fx-background-color: #3A3D52; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;"));
                iconItem.setOnMouseExited(e -> iconItem.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 4;"));
                final String fname = iconFile;
                iconItem.setOnMouseClicked(e -> { sendIconMessage(fname); emojiPopup.hide(); });
                Tooltip.install(iconItem, new Tooltip(iconFile.replace(".png", "")));
                iconGrid.getChildren().add(iconItem);
            } catch (Exception ignored) {}
        }

        ScrollPane iconScroll = new ScrollPane(iconGrid);
        iconScroll.setFitToWidth(true);
        iconScroll.setStyle("-fx-background-color: transparent;");
        iconTab.setContent(iconScroll);
        pickerTabs.getTabs().add(iconTab);

        emojiPopup.getContent().add(pickerTabs);
    }

    private static final String[] ICON_FILES = {
        "Slightly Smiling Face Emoji.png", "Upside-Down Face Emoji.png",
        "Smiling Face with Tightly Closed eyes.png", "Tears of Joy Emoji.png",
        "Smirk Face Emoji.png", "Sad Face Emoji.png", "Nerd Emoji With Glasses.png",
        "Smiling Face Emoji with Blushed Cheeks.png", "Sunglasses Emoji.png",
        "Hungry Face Emoji.png", "Face without Mouth Emoji.png", "Thinking Emoji.png",
        "Heart Eyes Emoji.png", "Hushed Face Emoji.png", "Eye Roll Emoji.png",
        "Cold Sweat Emoji.png", "OMG Face Emoji.png", "Flushed Face Emoji.png",
        "Smiling Face with Halo.png", "Sleeping Emoji.png", "Very Mad Emoji.png",
        "Very Angry Emoji.png", "Loudly Crying Face Emoji.png",
        "Disappointed but Relieved Face Emoji.png", "Smiling Devil Emoji.png",
        "Alien Emoji.png", "heart.png", "Thumbs Up Hand Sign Emoji.png"
    };

    private void sendIconMessage(String filename) {
        if (active == null) return;
        String content = "[ICON:" + filename + "]";
        long ts = System.currentTimeMillis();
        JsonObject msgData = new JsonObject();
        if (active.isGroup) {
            msgData.addProperty("groupId", active.uid);
        } else {
            msgData.addProperty("receiverId", active.uid);
        }
        msgData.addProperty("content", content);
        msgData.addProperty("messageType", "IMAGE");
        if (replyToMsgId > 0) {
            msgData.addProperty("replyToId", replyToMsgId);
            msgData.addProperty("replyToContent", replyToContent);
            msgData.addProperty("replyToSender", replyToSender);
        }
        client.send(active.isGroup ? Protocol.TYPE_GROUP_MESSAGE : Protocol.TYPE_MESSAGE, msgData);
        bubble("Bạn", content, ts, true, active.uid, "IMAGE", "SENT", ts);
        active.lastMsg = "🖼 Icon";
        active.time = timeFmt.format(new Date(ts));
        listView.refresh();
        cancelReply();
    }

    private void sendSticker(String packId, int index) {
        if (active == null) return;
        String stickerContent = com.messenger.client.ui.StickerManager.encodeSticker(packId, index);
        sendMessage(stickerContent);
    }

    private void sendMessage(String content) {
        if (content.isEmpty() || active == null) return;
        long ts = System.currentTimeMillis();
        if (active.isGroup) {
            JsonObject d = new JsonObject();
            d.addProperty("groupId", active.uid);
            d.addProperty("content", content);
            client.send(Protocol.TYPE_GROUP_MESSAGE, d);
        } else {
            JsonObject d = new JsonObject();
            d.addProperty("receiverId", active.uid);
            d.addProperty("content", content);
            client.send(Protocol.TYPE_MESSAGE, d);
        }
        // Hiển thị sticker to trong bubble
        String displayContent = com.messenger.client.ui.StickerManager.decodeSticker(content);
        if (displayContent == null) displayContent = content;
        bubble("B\u1EA1n", displayContent, ts, true, active != null ? active.uid : 0,
              content.startsWith("[STICKER:") ? "STICKER" : "TEXT", "SENT", ts);
        active.lastMsg = content.startsWith("[STICKER:") ? "\uD83C\uDF9F Sticker" : content;
        active.time = timeFmt.format(new Date(ts));
        listView.refresh();
        input.clear();
    }

    private void toggleEmojiPicker() {
        if (emojiPopup.isShowing()) { emojiPopup.hide(); return; }
        javafx.geometry.Bounds b = emojiBtn.localToScreen(emojiBtn.getBoundsInLocal());
        // Hiện popup phía trên nút; dùng prefHeight=330 vì getHeight()=0 khi chưa show lần nào
        double popupH = emojiPopup.getHeight() > 0 ? emojiPopup.getHeight() : 330;
        emojiPopup.show(emojiBtn, b.getMinX(), b.getMinY() - popupH);
    }

    private void insertEmoji(String emoji) {
        int pos = input.getCaretPosition();
        String t = input.getText();
        input.setText(t.substring(0,pos)+emoji+t.substring(pos));
        input.positionCaret(pos+emoji.length()); input.requestFocus();
    }

    // ==================== Search dropdown ====================

    /** Hiển thị kết quả tìm kiếm trong dropdown dưới ô search */
    public void showSearchResults(java.util.List<JsonObject> users) {
        Platform.runLater(() -> {
            searchItems.clear();
            searchIndex.clear();
            if (users == null || users.isEmpty()) {
                hideSearchResults();
                return;
            }
            int idx = 0;
            for (JsonObject u : users) {
                long uid = u.has("id") ? u.get("id").getAsLong() : -1;
                if (uid == currentUser.getId()) continue; // bỏ qua chính mình
                String nm = u.has("displayName") ? u.get("displayName").getAsString() : u.get("username").getAsString();
                String un = u.has("username") ? u.get("username").getAsString() : "";
                String online = "ONLINE".equals(u.has("presence") ? u.get("presence").getAsString() : "") ? "\u25CF" : "\u25CB";
                searchItems.add(nm + "  (" + un + ")  ID:" + uid + "  " + online);
                searchIndex.put(idx, u);
                idx++;
            }
            if (searchItems.isEmpty()) {
                hideSearchResults();
                return;
            }

            // Hiển thị popup ngay dưới search wrap, căn theo chiều rộng của wrapper
            javafx.geometry.Bounds b = searchWrap != null
                ? searchWrap.localToScreen(searchWrap.getBoundsInLocal())
                : searchField.localToScreen(searchField.getBoundsInLocal());
            if (b != null) {
                searchResultView.setPrefWidth(b.getWidth());
                if (!searchPopup.isShowing()) {
                    searchPopup.show(searchField.getScene().getWindow(), b.getMinX(), b.getMaxY() + 2);
                }
            }
            searchResultView.setPrefHeight(Math.min(280, searchItems.size() * 56 + 8));
        });
    }

    private void hideSearchResults() {
        Platform.runLater(() -> {
            if (searchPopup != null && searchPopup.isShowing()) searchPopup.hide();
        });
    }

    /** Hiển thị menu hành động khi click vào kết quả tìm kiếm */
    private void showSearchUserActions(JsonObject user) {
        hideSearchResults();
        long uid = user.has("id") ? user.get("id").getAsLong() : -1;
        String nm = user.has("displayName") ? user.get("displayName").getAsString() : "?";
        String un = user.has("username") ? user.get("username").getAsString() : "?";

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("search-context-menu");

        MenuItem header = new MenuItem(nm + " (@" + un + ")");
        header.setDisable(true);
        header.getStyleClass().add("search-context-header");

        MenuItem chat = new MenuItem("\uD83D\uDCAC Nh\u1EAFn tin");
        chat.setOnAction(e -> {
            addConv(nm, uid, false, false);
            highlightConversation(uid);
            // Đóng search popup và clear
            searchField.clear();
        });

        MenuItem addFriend = new MenuItem("\uD83D\uDC65 G\u1EEDi l\u1EDDi m\u1EDDi k\u1EBFt b\u1EA1n");
        addFriend.setOnAction(e -> {
            JsonObject req = new JsonObject();
            req.addProperty("toUserId", uid);
            client.send(Protocol.TYPE_FRIEND_REQUEST, req);
            callback.showAlert("L\u1EDDi m\u1EDDi", "\u0110\u00E3 g\u1EEDi l\u1EDDi m\u1EDDi \u0111\u1EBFn " + nm);
            searchField.clear();
        });

        menu.getItems().addAll(header, new SeparatorMenuItem(), chat, addFriend);
        menu.show(searchField, null, 0, 0);
        // Đặt vị trí menu gần search field
        javafx.geometry.Bounds b = searchField.localToScreen(searchField.getBoundsInLocal());
        menu.show(searchField, b.getMinX() + 20, b.getMaxY() - 40);
    }

    private void open(ConvItem c) {
        active=c; c.unread=0; listView.refresh(); hdrName.setText(c.name);
        if(c.online&&!c.isGroup) {
            hdrStatus.setText("\u25CF \u0110ang ho\u1EA1t \u0111\u1ED9ng");
            hdrStatus.setStyle("-fx-text-fill:#31D158; -fx-font-size: 12px;");
        } else if(c.isGroup) {
            hdrStatus.setText("Nh\u00F3m chat");
            hdrStatus.setStyle("-fx-text-fill:#65676B; -fx-font-size: 12px;");
        } else {
            hdrStatus.setText("Kh\u00F4ng ho\u1EA1t \u0111\u1ED9ng");
            hdrStatus.setStyle("-fx-text-fill:#65676B; -fx-font-size: 12px;");
        }
        hdrAvatar.getChildren().clear();
        hdrAvatar.getChildren().add(makeAvatar(c.initials,c.avatarColor,38,c.online&&!c.isGroup,c.avatarUrl));
        msgArea.getChildren().clear(); msgScroll.setVvalue(1.0);
        // Load lịch sử từ file local
        if (!c.isGroup && c.uid > 0) {
            var history = ChatHistoryManager.loadHistory(currentUser.getId(), c.uid);
            for (var msg : history) {
                String type = msg.messageType != null ? msg.messageType : (msg.content != null && msg.content.startsWith("[STICKER:") ? "STICKER" : "TEXT");
                String display = "STICKER".equals(type) ? com.messenger.client.ui.StickerManager.decodeSticker(msg.content) : msg.content;
                if (display == null) display = msg.content;
                bubble(msg.senderName, display, msg.timestamp, msg.isMine, 0, type, msg.status != null ? msg.status : "SENT", msg.messageId > 0 ? msg.messageId : msg.timestamp);
            }
            requestHistory(c.uid); // cập nhật từ server nếu có mới
        } else if (c.isGroup) {
            // Load group messages from server
            JsonObject d = new JsonObject();
            d.addProperty("groupId", c.uid);
            d.addProperty("limit", 50);
            client.send(Protocol.TYPE_GET_MESSAGE_HISTORY, d);
        }
    }

    // --- Server message handlers ---

    /**
     * Nhận và route tất cả message từ server.
     * Được gọi từ MainView.onServerMessage().
     */
    public void handleServerMessage(JsonObject m) {
        String type = m.has("type") ? m.get("type").getAsString() : "";
        switch (type) {
            case Protocol.TYPE_MESSAGE:
                handleMessage(m, false);
                break;
            case Protocol.TYPE_GROUP_MESSAGE:
                handleMessage(m, true);
                break;
            case Protocol.TYPE_TYPING:
                handleTyping(m);
                break;
            case Protocol.TYPE_UNSEND:
                handleUnsend(m);
                break;
            case Protocol.TYPE_REACTION:
                handleReaction(m);
                break;
            case Protocol.TYPE_FILE_TRANSFER:
                handleFile(m);
                break;
            case Protocol.TYPE_GROUP_UPDATE:
                handleGroupUpdate(m);
                break;
            case Protocol.TYPE_GROUP_LEAVE:
                if (m.has("kicked") && m.get("kicked").getAsBoolean()) {
                    long gid = m.has("groupId") ? m.get("groupId").getAsLong() : -1;
                    if (gid > 0) removeConv(gid);
                }
                break;
            case Protocol.TYPE_SUCCESS:
                handleSuccess(m);
                break;
            case Protocol.TYPE_MESSAGE_STATUS:
                // Cập nhật trạng thái tin nhắn
                Platform.runLater(() -> {
                    long msgId = m.has("messageId") ? m.get("messageId").getAsLong() : -1;
                    String status = m.has("status") ? m.get("status").getAsString() : "SENT";
                    for (javafx.scene.Node n : msgArea.getChildren()) {
                        if (n instanceof HBox) {
                            Object o = n.getProperties().get("msgId");
                            if (o != null && o.equals(msgId)) {
                                // Cập nhật icon trạng thái
                                HBox row = (HBox) n;
                                updateMessageStatus(row, status);
                                break;
                            }
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    private void updateMessageStatus(HBox row, String status) {
        for (javafx.scene.Node child : row.getChildren()) {
            if (child instanceof VBox) {
                VBox bubble = (VBox) child;
                for (javafx.scene.Node inner : bubble.getChildren()) {
                    if (inner instanceof HBox) {
                        HBox statusRow = (HBox) inner;
                        for (javafx.scene.Node sChild : statusRow.getChildren()) {
                            if (sChild.getStyleClass().contains("bubble-status-icon")) {
                                Label icon = (Label) sChild;
                                icon.setText(getStatusIcon(status));
                                if ("SEEN".equals(status)) {
                                    icon.setStyle("-fx-text-fill: #31D158; -fx-font-size: 10px; -fx-font-weight: bold;");
                                } else {
                                    icon.setStyle(null);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void handleMessage(JsonObject m, boolean grp) {
        long sid=m.has("senderId")?m.get("senderId").getAsLong():-1; String ct=m.has("content")?m.get("content").getAsString():"";
        String sn=m.has("senderName")?m.get("senderName").getAsString():"\u1EA8n danh"; long ts=m.has("timestamp")?m.get("timestamp").getAsLong():System.currentTimeMillis();
        boolean mine=(sid==currentUser.getId());
        long cid=grp?(m.has("groupId")?m.get("groupId").getAsLong():-1):(mine&&active!=null?active.uid:sid);
        long msgId = m.has("messageId") ? m.get("messageId").getAsLong() : ts;

        // Phát hiện sticker & reply
        String displayCt = ct;
        String msgType = m.has("messageType") ? m.get("messageType").getAsString() : "TEXT";
        boolean hasReply = m.has("replyToId") && m.get("replyToId").getAsLong() > 0;
        String replyToContent = hasReply ? m.get("replyToContent").getAsString() : "";
        String replyToSender = hasReply ? m.get("replyToSender").getAsString() : "";

        if ((msgType == null || "TEXT".equals(msgType)) && ct != null && ct.startsWith("[STICKER:")) {
            msgType = "STICKER";
            displayCt = com.messenger.client.ui.StickerManager.decodeSticker(ct);
            if (displayCt == null) displayCt = ct;
        }

        if(active!=null&&((grp&&active.isGroup&&active.uid==cid)||(!grp&&!active.isGroup&&(active.uid==cid||sid==currentUser.getId()))))
            bubble(sn, displayCt, ts, mine, active!=null?active.uid:cid, msgType, "DELIVERED", msgId, replyToContent, replyToSender);
        ConvItem cv=convMap.get(cid);
        String lastMsg = previewForMessage(msgType, ct);
        if(cv!=null){ cv.lastMsg=lastMsg; cv.time=timeFmt.format(new Date(ts)); if(!mine&&(active==null||active.uid!=cid))cv.unread++; listView.refresh(); }
        if(!mine&&!client.getPrimaryStage().isFocused()) {
            String notifText = previewForMessage(msgType, ct);
            client.getNotificationManager().showNotification("Tin nh\u1EAFn t\u1EEB "+sn, notifText);
        }
    }

    public void handleTyping(JsonObject m) {
        long sid=m.has("senderId")?m.get("senderId").getAsLong():-1; boolean ty=m.has("typing")&&m.get("typing").getAsBoolean();
        if(active!=null&&!active.isGroup&&active.uid==sid) typingLbl.setText(ty?active.name+" \u0111ang nh\u1EADp...":"");
    }

    public void handleUnsend(JsonObject m) {
        long mid=m.has("messageId")?m.get("messageId").getAsLong():-1;
        msgArea.getChildren().removeIf(n->{ Object o=n.getProperties().get("msgId"); return o!=null&&o.equals(mid); });
    }

    public void handleReaction(JsonObject m) {
        long mid = m.has("messageId") ? m.get("messageId").getAsLong() : -1;
        String r = m.has("reaction") ? m.get("reaction").getAsString() : "";
        Platform.runLater(() -> {
            for (javafx.scene.Node n : msgArea.getChildren()) {
                if (!(n instanceof HBox)) continue;
                HBox row = (HBox) n;
                Object o = row.getProperties().get("msgId");
                if (o == null || !o.equals(mid)) continue;

                // Tìm VBox bubble (có thể ở index 0 hoặc 1 tùy có avatar hay không)
                VBox bubble = null;
                for (javafx.scene.Node child : row.getChildren()) {
                    if (child instanceof VBox) { bubble = (VBox) child; break; }
                }
                if (bubble == null) break;

                // Tìm hoặc tạo reaction bar (HBox với style class "reaction-bar")
                HBox reactionBar = null;
                for (javafx.scene.Node child : bubble.getChildren()) {
                    if (child instanceof HBox && child.getStyleClass().contains("reaction-bar")) {
                        reactionBar = (HBox) child; break;
                    }
                }
                if (reactionBar == null) {
                    reactionBar = new HBox(4);
                    reactionBar.getStyleClass().add("reaction-bar");
                    reactionBar.setPadding(new Insets(2, 0, 0, 0));
                    // Chèn trước statusRow (phần tử HBox cuối cùng)
                    int insertIdx = bubble.getChildren().size();
                    for (int i = bubble.getChildren().size() - 1; i >= 0; i--) {
                        if (bubble.getChildren().get(i) instanceof HBox) { insertIdx = i; break; }
                    }
                    bubble.getChildren().add(insertIdx, reactionBar);
                }

                // Kiểm tra reaction đã tồn tại chưa (tránh duplicate)
                final HBox bar = reactionBar;
                boolean exists = bar.getChildren().stream()
                    .anyMatch(c -> c instanceof Label && ((Label) c).getText().equals(r));
                if (!exists) {
                    Label chip = new Label(r);
                    chip.getStyleClass().add("reaction-chip");
                    chip.setPadding(new Insets(1, 6, 1, 6));
                    chip.setFont(Font.font(13));
                    bar.getChildren().add(chip);
                }
                break;
            }
        });
    }

    public void handleSuccess(JsonObject m) {
        if (!m.has("data")) return;
        JsonElement dataElement = m.get("data");
        if (dataElement == null || dataElement.isJsonNull()) return;
        if (dataElement.isJsonArray()) {
            for (JsonElement e : dataElement.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject g = e.getAsJsonObject();
                if (g.has("name") && g.has("id")) {
                    ConvItem ci = addConv(g.get("name").getAsString(), g.get("id").getAsLong(), false, true);
                    if (ci != null && g.has("avatarUrl")) {
                        String av = g.get("avatarUrl").getAsString();
                        if (av != null && !av.isEmpty()) ci.avatarUrl = av;
                    }
                }
            }
            return;
        }
        if (!dataElement.isJsonObject()) return;
        JsonObject d=dataElement.getAsJsonObject();

        // Xử lý message history từ server
        if(d.has("messages") && d.get("messages").isJsonArray()) {
            if (active != null) {
                if (d.has("friendId") && (active.isGroup || active.uid != d.get("friendId").getAsLong())) return;
                if (d.has("groupId") && (!active.isGroup || active.uid != d.get("groupId").getAsLong())) return;
            }
            loadMessageHistory(d.getAsJsonArray("messages"));
        }

        if(d.has("friends")&&d.get("friends").isJsonArray()) for(JsonElement e:d.getAsJsonArray("friends")){
            JsonObject u=e.getAsJsonObject(); String nm=u.has("displayName")?u.get("displayName").getAsString():u.get("username").getAsString();
            long id=u.get("id").getAsLong(); if(id!=currentUser.getId()) {
                ConvItem ci = addConv(nm,id,"ONLINE".equals(u.has("presence")?u.get("presence").getAsString():""),false);
                if (ci != null && u.has("avatarUrl")) {
                    String av = u.get("avatarUrl").getAsString();
                    if (av != null && !av.isEmpty()) ci.avatarUrl = av;
                }
            }
        }
        if(d.has("groups")&&d.get("groups").isJsonArray()) for(JsonElement e:d.getAsJsonArray("groups")){
            JsonObject g=e.getAsJsonObject();
            ConvItem ci = addConv(g.get("name").getAsString(),g.get("id").getAsLong(),false,true);
            if (ci != null && g.has("avatarUrl")) {
                String av = g.get("avatarUrl").getAsString();
                if (av != null && !av.isEmpty()) ci.avatarUrl = av;
            }
        }

        // Xử lý group info response — getGroupInfo() trả về ownerId (từ DB)
        if(d.has("members") && d.get("members").isJsonArray() && d.has("id") && d.has("ownerId")) {
            Platform.runLater(() -> openGroupInfoDialog(d));
            return;
        }

        // Nhóm vừa tạo — server Gson-serialize Group model, có creatorId
        if (d.has("id") && d.has("name") && d.has("creatorId")) {
            long gid = d.get("id").getAsLong();
            String gname = d.get("name").getAsString();
            Platform.runLater(() -> {
                addConv(gname, gid, false, true);
                listView.refresh();
            });
        }
    }

    /** Xử lý GROUP_UPDATE từ server (đổi tên, avatar, description) */
    public void handleGroupUpdate(JsonObject m) {
        long gid = m.has("groupId") ? m.get("groupId").getAsLong() : -1;
        if (gid <= 0) return;
        Platform.runLater(() -> {
            ConvItem c = convMap.get(gid);
            if (c == null) return;
            if (m.has("name")) { c.name = m.get("name").getAsString(); c.initials = c.name.length()>=2?c.name.substring(0,2).toUpperCase():c.name.substring(0,1).toUpperCase(); }
            if (m.has("avatarUrl")) { c.avatarUrl = m.get("avatarUrl").getAsString(); }
            listView.refresh();
            if (active != null && active.uid == gid) {
                if (m.has("name")) hdrName.setText(c.name);
                if (m.has("avatarUrl")) { updateHeaderAvatar(c); }
            }
        });
    }

    /** Cập nhật avatar header */
    private void updateHeaderAvatar(ConvItem c) {
        hdrAvatar.getChildren().clear();
        hdrAvatar.getChildren().add(makeAvatar(c.initials,c.avatarColor,38,c.online&&!c.isGroup,c.avatarUrl));
    }

    public void handleFile(JsonObject m) {
        String fn=m.has("fileName")?m.get("fileName").getAsString():"file"; long fsz=m.has("fileSize")?m.get("fileSize").getAsLong():0;
        String sn=m.has("senderName")?m.get("senderName").getAsString():"\u1EA8n danh";
        long ts=System.currentTimeMillis();
        // Kiểm tra nếu là file ảnh
        String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.')+1).toLowerCase() : "";
        boolean isImage = ext.equals("png")||ext.equals("jpg")||ext.equals("jpeg")||ext.equals("gif")||ext.equals("bmp")||ext.equals("webp");
        if (isImage) {
            // Đọc file chunks và hiển thị ảnh (sẽ cập nhật sau khi nhận đủ chunks)
            // Tạm thời hiển thị text
            bubble(sn, "\uD83D\uDDBC " + fn + " (" + fmtSz(fsz) + ")", ts, false, active!=null?active.uid:0);
        } else {
            // Bubble file
            HBox row = new HBox(8);
            row.setPadding(new Insets(2, 14, 4, 14));
            VBox bubbleBox = new VBox(6);
            bubbleBox.setPadding(new Insets(8, 12, 8, 12));
            bubbleBox.setMaxWidth(320);
            bubbleBox.getStyleClass().add("bubble-theirs");

            Label fileIcon = new Label("\uD83D\uDCCE");
            fileIcon.setFont(Font.font(32));
            Label nameLbl = new Label(fn);
            nameLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
            nameLbl.getStyleClass().add("conv-name");
            Label sizeLbl = new Label(fmtSz(fsz));
            sizeLbl.setFont(Font.font("SansSerif", 11));
            sizeLbl.setStyle("-fx-text-fill: #65676B;");

            HBox fileInfo = new HBox(10, fileIcon, new VBox(2, nameLbl, sizeLbl));
            fileInfo.setAlignment(Pos.CENTER_LEFT);
            bubbleBox.getChildren().add(fileInfo);

            HBox statusRow = new HBox(4);
            statusRow.setAlignment(Pos.CENTER_RIGHT);
            Label timeLbl = new Label(timeFmt.format(new Date(ts)));
            timeLbl.getStyleClass().add("bubble-time-theirs");
            statusRow.getChildren().add(timeLbl);
            bubbleBox.getChildren().add(statusRow);

            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(bubbleBox);
            msgArea.getChildren().add(row);
            Platform.runLater(() -> msgScroll.setVvalue(1.0));
        }
    }

    /**
     * Load message history from server and display in chat area.
     */
    public void loadMessageHistory(JsonArray messages) {
        Platform.runLater(() -> {
            msgArea.getChildren().clear();
            boolean hasMessages = messages != null && messages.size() > 0;
            if (!hasMessages) {
                msgArea.getChildren().add(buildNoMessageState());
                return;
            }
            // Messages come in reverse chronological order from server; display oldest first
            java.util.List<JsonObject> list = new java.util.ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                list.add(messages.get(i).getAsJsonObject());
            }
            java.util.Collections.reverse(list);
            for (JsonObject m : list) {
                long sid = m.has("senderId") ? m.get("senderId").getAsLong() : -1;
                String ct = m.has("content") ? m.get("content").getAsString() : "";
                String sn = m.has("senderName") ? m.get("senderName").getAsString() : "\u1EA8n danh";
                long ts = m.has("timestamp") ? m.get("timestamp").getAsLong() : System.currentTimeMillis();
                boolean mine = (sid == currentUser.getId());
                long msgId = m.has("id") ? m.get("id").getAsLong() : ts;
                String msgType = m.has("messageType") ? m.get("messageType").getAsString() : "TEXT";
                String status = m.has("status") ? m.get("status").getAsString() : "DELIVERED";
                String displayCt = ct;
                if ((msgType == null || "TEXT".equals(msgType)) && ct != null && ct.startsWith("[STICKER:")) {
                    msgType = "STICKER";
                    displayCt = com.messenger.client.ui.StickerManager.decodeSticker(ct);
                    if (displayCt == null) displayCt = ct;
                }
                bubble(sn, displayCt, ts, mine, 0, msgType, status, msgId); // 0 = không lưu (đã lưu từ server)
            }
            Platform.runLater(() -> msgScroll.setVvalue(1.0));
        });
    }

    /**
     * Request message history for the current conversation.
     */
    public void requestHistory(long friendId) {
        JsonObject data = new JsonObject();
        data.addProperty("friendId", friendId);
        data.addProperty("limit", 50);
        client.send(Protocol.TYPE_GET_MESSAGE_HISTORY, data);
    }

    // --- Bubbles & send ---

    /**
     * Hiển thị bubble tin nhắn với style Messenger:
     * - Mine: nền xanh dương, bo góc 18px, căn phải
     * - Theirs: nền xám, bo góc 18px, căn trái
     * - Group chat: hiển thị tên người gửi phía trên bubble (bên nhận)
     * - Status icon: ✓ (đã gửi), ✓✓ (đã nhận), ✓✓ xanh (đã xem)
     */
    private void bubble(String sn, String ct, long ts, boolean mine, long friendId) {
        bubble(sn, ct, ts, mine, friendId, "TEXT", "SENT", ts);
    }

    private void bubble(String senderName, String content, long timestamp, boolean mine,
                        long friendId, String messageType, String status, long messageId) {
        bubble(senderName, content, timestamp, mine, friendId, messageType, status, messageId, null, null);
    }

    private void bubble(String senderName, String content, long timestamp, boolean mine,
                        long friendId, String messageType, String status, long messageId,
                        String replyToContent, String replyToSender) {
        msgArea.getChildren().removeIf(n -> n.getStyleClass().contains("chat-empty-state"));

        // Lưu vào lịch sử local
        if (friendId > 0) {
            ChatHistoryManager.saveMessage(currentUser.getId(), friendId,
                    mine ? currentUser.getId() : friendId, senderName, content, timestamp, mine,
                    messageType, status, messageId > 0 ? messageId : timestamp);
        }

        // Hàng chính: avatar (nếu theirs + group) + bubble
        HBox row = new HBox(8);
        row.setPadding(new Insets(2, 14, 4, 14));
        row.getProperties().put("msgId", messageId > 0 ? messageId : timestamp);

        // --- Khung bubble ---
        VBox bubbleBox = new VBox(3);
        bubbleBox.setPadding(new Insets(10, 14, 8, 14));
        bubbleBox.setMaxWidth(340);

        if (mine) {
            bubbleBox.getStyleClass().add("bubble-mine");
        } else {
            bubbleBox.getStyleClass().add("bubble-theirs");
        }

        // --- Tên người gửi (group chat, tin nhắn của người khác) ---
        if (!mine && active != null && active.isGroup && senderName != null && !senderName.isEmpty()) {
            Label nameLabel = new Label(senderName);
            nameLabel.getStyleClass().add("bubble-sender-name");
            nameLabel.setPadding(new Insets(0, 0, 2, 0));
            bubbleBox.getChildren().add(nameLabel);
        }

        // --- Reply quote (nếu có) ---
        if (replyToContent != null && !replyToContent.isEmpty()) {
            VBox replyQuote = new VBox(1);
            replyQuote.getStyleClass().add("bubble-reply-quote");
            Label rqName = new Label(replyToSender != null ? replyToSender : "");
            rqName.getStyleClass().add("bubble-reply-qname");
            rqName.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
            Label rqText = new Label(replyToContent);
            rqText.getStyleClass().add("bubble-reply-qtext");
            rqText.setFont(Font.font("SansSerif", 11));
            rqText.setWrapText(true);
            rqText.setMaxWidth(200);
            replyQuote.getChildren().addAll(rqName, rqText);
            bubbleBox.getChildren().add(replyQuote);
        }

        // --- Nội dung tin nhắn ---
        if ("STICKER".equals(messageType) && content != null && !content.isEmpty()) {
            // Sticker: emoji lớn
            Label stickerLabel = new Label(content);
            stickerLabel.setFont(Font.font(48));
            stickerLabel.setPadding(new Insets(4, 4, 0, 4));
            bubbleBox.getChildren().add(stickerLabel);
            // Bỏ padding của bubble cho sticker
            bubbleBox.setPadding(new Insets(6, 8, 4, 8));
        } else if ("IMAGE".equals(messageType) && content != null && !content.isEmpty()) {
            if (content.startsWith("[ICON:")) {
                addIconContent(bubbleBox, content, mine);
            } else {
                addImageContent(bubbleBox, content, mine);
            }
        } else if ("FILE".equals(messageType) && content != null && !content.isEmpty()) {
            addFileContent(bubbleBox, content, mine);
        } else {
            addTextContent(bubbleBox, content, mine);
        }

        // --- Hàng thời gian + trạng thái ---
        HBox statusRow = new HBox(5);
        statusRow.setAlignment(Pos.CENTER_RIGHT);
        statusRow.setPadding(new Insets(2, 0, 0, 0));

        Label timeLabel = new Label(timeFmt.format(new Date(timestamp)));
        timeLabel.getStyleClass().add(mine ? "bubble-time-mine" : "bubble-time-theirs");
        statusRow.getChildren().add(timeLabel);

        if (mine) {
            Label statusIcon = new Label(getStatusIcon(status));
            statusIcon.getStyleClass().add("bubble-status-icon");
            if ("SEEN".equals(status)) {
                statusIcon.setStyle("-fx-text-fill: #31D158; -fx-font-size: 10px; -fx-font-weight: bold;");
            } else if ("DELIVERED".equals(status)) {
                statusIcon.setStyle("-fx-text-fill: rgba(255,255,255,0.65); -fx-font-size: 10px; -fx-font-weight: bold;");
            }
            statusRow.getChildren().add(statusIcon);
        }
        bubbleBox.getChildren().add(statusRow);

        // --- Avatar bên trái cho tin nhắn của người khác ---
        if (!mine && active != null) {
            StackPane av = makeAvatar(active.initials, active.avatarColor, 28, active.online, active.avatarUrl);
            av.setTranslateY(4);
            row.getChildren().addAll(av, bubbleBox);
        } else {
            row.getChildren().add(bubbleBox);
        }

        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        msgArea.getChildren().add(row);

        // Context menu: Thu hồi (mine), Trả lời, Chuyển tiếp, Cảm xúc
        final long fMsgId = messageId;
        final boolean fMine = mine;
        bubbleBox.setOnContextMenuRequested(ev -> {
            ContextMenu bubbleMenu = new ContextMenu();

            // Emoji reactions \u2014 inline HBox (kh\u00F4ng c\u1EA7n hover sub-menu)
            HBox rxBox = new HBox(4);
            rxBox.setPadding(new Insets(4, 8, 4, 8));
            rxBox.setAlignment(Pos.CENTER_LEFT);
            String[] rxEmojis = {"\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE21"};
            for (String rx : rxEmojis) {
                Button rxBtn = new Button(rx);
                rxBtn.setFont(Font.font(18));
                rxBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4;");
                rxBtn.setOnMouseEntered(e2 -> rxBtn.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 2 4;"));
                rxBtn.setOnMouseExited(e2 -> rxBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4;"));
                rxBtn.setOnAction(e -> { sendRx(fMsgId, rx); bubbleMenu.hide(); });
                rxBox.getChildren().add(rxBtn);
            }
            CustomMenuItem reactionsRow = new CustomMenuItem(rxBox, false);
            bubbleMenu.getItems().add(reactionsRow);
            bubbleMenu.getItems().add(new SeparatorMenuItem());

            MenuItem replyItem = new MenuItem("\u21A9 Tr\u1EA3 l\u1EDDi");
            replyItem.setOnAction(e -> startReply(senderName, content, fMsgId, row));
            bubbleMenu.getItems().add(replyItem);

            MenuItem forwardItem = new MenuItem("\u27A1 Chuy\u1EC3n ti\u1EBFp");
            forwardItem.setOnAction(e -> forwardMessage(senderName, content, fMsgId));
            bubbleMenu.getItems().add(forwardItem);

            if (fMine) {
                bubbleMenu.getItems().add(new SeparatorMenuItem());
                MenuItem unsendItem = new MenuItem("\uD83D\uDDD1 Thu h\u1ED3i");
                unsendItem.setOnAction(e -> unsend(fMsgId, row));
                bubbleMenu.getItems().add(unsendItem);
            }
            bubbleMenu.show(bubbleBox, ev.getScreenX(), ev.getScreenY());
            ev.consume();
        });

        Platform.runLater(() -> msgScroll.setVvalue(1.0));
    }

    // ==================== Reply ====================
    private HBox replyQuoteBar;
    private long replyToMsgId = 0;
    private String replyToContent = "";
    private String replyToSender = "";

    private void startReply(String senderName, String content, long msgId, HBox targetRow) {
        replyToMsgId = msgId;
        replyToContent = content;
        replyToSender = senderName;
        showReplyQuote();
        input.requestFocus();
    }

    private void showReplyQuote() {
        if (replyQuoteBar == null) {
            replyQuoteBar = new HBox(8);
            replyQuoteBar.setPadding(new Insets(6, 14, 2, 14));
            replyQuoteBar.setAlignment(Pos.CENTER_LEFT);
            replyQuoteBar.getStyleClass().add("reply-quote-bar");
        }
        replyQuoteBar.getChildren().clear();

        VBox quoteContent = new VBox(2);
        quoteContent.setMaxWidth(400);
        Label qn = new Label(replyToSender);
        qn.getStyleClass().add("reply-quote-name");
        qn.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        Label qc = new Label(replyToContent);
        qc.getStyleClass().add("reply-quote-text");
        qc.setFont(Font.font("SansSerif", 11));
        qc.setWrapText(true);
        qc.setMaxWidth(350);
        quoteContent.getChildren().addAll(qn, qc);

        Button closeReply = new Button("\u2716");
        closeReply.getStyleClass().add("btn-icon");
        closeReply.setMinSize(24, 24);
        closeReply.setOnAction(e -> cancelReply());
        HBox.setHgrow(quoteContent, Priority.ALWAYS);

        if (chatAreaVBox == null) return;
        int idx = chatAreaVBox.getChildren().indexOf(typingLbl);
        if (idx >= 0 && !chatAreaVBox.getChildren().contains(replyQuoteBar)) {
            chatAreaVBox.getChildren().add(idx + 1, replyQuoteBar);
            replyQuoteBar.setVisible(true);
        }
        replyQuoteBar.getChildren().addAll(closeReply, quoteContent);
    }

    private void cancelReply() {
        replyToMsgId = 0;
        replyToContent = "";
        replyToSender = "";
        if (replyQuoteBar != null) {
            replyQuoteBar.getChildren().clear();
            replyQuoteBar.setVisible(false);
            if (chatAreaVBox != null) chatAreaVBox.getChildren().remove(replyQuoteBar);
        }
    }

    private void forwardMessage(String senderName, String rawContent, long msgId) {
        if (listItems.isEmpty()) { callback.showAlert("Chuy\u1EC3n ti\u1EBFp", "Kh\u00F4ng c\u00F3 h\u1ED9i tho\u1EA1i n\u00E0o."); return; }
        Dialog<ConvItem> dialog = new Dialog<>();
        dialog.setTitle("Chuy\u1EC3n ti\u1EBFp tin nh\u1EAFn");
        dialog.setHeaderText(null);

        String preview = rawContent.length() > 80 ? rawContent.substring(0, 80) + "\u2026" : rawContent;
        Label previewLbl = new Label("\"" + preview + "\"");
        previewLbl.setWrapText(true);
        previewLbl.setStyle("-fx-text-fill: #999; -fx-font-style: italic; -fx-padding: 0 0 8 0;");

        ListView<ConvItem> pick = new ListView<>(listItems);
        pick.setPrefHeight(220);
        pick.setCellFactory(lv -> new javafx.scene.control.ListCell<ConvItem>() {
            @Override protected void updateItem(ConvItem c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : (c.isGroup ? "\uD83D\uDC65 " : "\uD83D\uDC64 ") + c.name);
            }
        });
        VBox body = new VBox(8, previewLbl, new Label("G\u1EEDi \u0111\u1EBFn:"), pick);
        body.setPadding(new Insets(14));
        body.setPrefWidth(320);
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? pick.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(target -> {
            if (target == null) { callback.showAlert("Chuy\u1EC3n ti\u1EBFp", "Vui l\u00F2ng ch\u1ECDn ng\u01B0\u1EDDi nh\u1EADn."); return; }
            String fwdContent = "[\u0110\u00E3 chuy\u1EC3n ti\u1EBFp t\u1EEB " + senderName + "]\n" + rawContent;
            JsonObject req = new JsonObject();
            if (target.isGroup) req.addProperty("groupId", target.uid);
            else req.addProperty("receiverId", target.uid);
            req.addProperty("content", fwdContent);
            req.addProperty("messageType", "TEXT");
            client.send(target.isGroup ? Protocol.TYPE_GROUP_MESSAGE : Protocol.TYPE_MESSAGE, req);
            callback.showAlert("Chuy\u1EC3n ti\u1EBFp", "\u0110\u00E3 g\u1EEDi \u0111\u1EBFn " + target.name);
        });
    }

    private void addTextContent(VBox bubbleBox, String content, boolean mine) {
        Text tx = new Text(content != null ? content : "");
        tx.setWrappingWidth(280);
        tx.getStyleClass().add(mine ? "bubble-text-mine" : "bubble-text-theirs");
        bubbleBox.getChildren().add(tx);
    }

    private void addImageContent(VBox bubbleBox, String content, boolean mine) {
        try {
            String imageSource = normalizeImageSource(content);
             Image img = new Image(imageSource, false);
             ImageView iv = new ImageView(img);
              if (img.isError()) {
                 addTextContent(bubbleBox, previewForMessage("IMAGE", content), mine);
                 return;}
            iv.setFitWidth(240);
            iv.setPreserveRatio(true);
            iv.getStyleClass().add("bubble-image");
            iv.setCursor(javafx.scene.Cursor.HAND);
            setTT(iv, "Click de xem anh toan man hinh");
            iv.setOnMouseClicked(e -> showImageViewer(imageSource));
            bubbleBox.getChildren().add(iv);
        } catch (Exception ex) {
            addTextContent(bubbleBox, previewForMessage("IMAGE", content), mine);
        }
    }

    private void addIconContent(VBox bubbleBox, String content, boolean mine) {
        String filename = content.substring(6, content.length() - 1);
        try {
            java.io.InputStream is = ChatTab.class.getResourceAsStream("/icons/" + filename);
            if (is == null) { addTextContent(bubbleBox, "🖼 Icon", mine); return; }
            Image img = new Image(is, 120, 120, true, true);
            if (img.isError()) { addTextContent(bubbleBox, "🖼 Icon", mine); return; }
            ImageView iv = new ImageView(img);
            iv.setFitWidth(120);
            iv.setFitHeight(120);
            iv.setPreserveRatio(true);
            iv.getStyleClass().add("bubble-image");
            bubbleBox.getChildren().add(iv);
            bubbleBox.setPadding(new Insets(6, 8, 4, 8));
        } catch (Exception ex) {
            addTextContent(bubbleBox, "🖼 Icon", mine);
        }
    }

    private void addFileContent(VBox bubbleBox, String content, boolean mine) {
        JsonObject meta = parseJsonObject(content);
        String fileName = meta != null && meta.has("fileName") ? meta.get("fileName").getAsString() : "file";
        long fileSize = meta != null && meta.has("fileSize") ? meta.get("fileSize").getAsLong() : 0;

        Label fileIcon = new Label("\uD83D\uDCCE");
        fileIcon.setFont(Font.font(32));
        Label nameLbl = new Label(fileName);
        nameLbl.getStyleClass().add("file-name-label");
        nameLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(220);
        Label sizeLbl = new Label(fileSize > 0 ? fmtSz(fileSize) : "");
        sizeLbl.setFont(Font.font("SansSerif", 11));

        VBox labels = new VBox(2, nameLbl, sizeLbl);
        HBox fileInfo = new HBox(10, fileIcon, labels);
        fileInfo.setAlignment(Pos.CENTER_LEFT);
        fileInfo.setCursor(javafx.scene.Cursor.HAND);
        setTT(fileInfo, "Click de tai file");
        fileInfo.setOnMouseClicked(e -> downloadFileMessage(content));
        bubbleBox.getChildren().add(fileInfo);
    }

    private String normalizeImageSource(String content) {
        if (content == null || content.isEmpty()) return "";
        // Icon từ classpath /icons/
        if (content.startsWith("[ICON:")) {
            String filename = content.substring(6, content.length() - 1);
            java.net.URL url = ChatTab.class.getResource("/icons/" + filename);
            return url != null ? url.toExternalForm() : "";
        }
        JsonObject meta = parseJsonObject(content);
        if (meta != null && meta.has("data")) {
            String mime = meta.has("mime") ? meta.get("mime").getAsString() : "image/png";
            String data = meta.get("data").getAsString();
            if (data.startsWith("data:image")) return data;
            return "data:" + mime + ";base64," + data;
        }
        if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("data:image")) {
            return content;
        }
        return "data:image/png;base64," + content;
    }

    private void showImageViewer(String imageSource) {
        if (imageSource == null || imageSource.isEmpty()) return;
        Stage viewer = new Stage();
        viewer.initOwner(client.getPrimaryStage());
        viewer.setTitle("Xem \u1ea3nh \u2014 Moji Messenger");

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #0A0A0A;");
        Image img = new Image(imageSource, false);
        if (img.isError()) return;

        ImageView full = new ImageView(img);
        full.setPreserveRatio(true);
        full.fitWidthProperty().bind(root.widthProperty().subtract(24));
        full.fitHeightProperty().bind(root.heightProperty().subtract(64));

        // Zoom b\u1eb1ng scroll chu\u1ed9t
        final double[] scale = {1.0};
        root.setOnScroll(e -> {
            double delta = e.getDeltaY() > 0 ? 1.12 : 0.89;
            scale[0] = Math.max(0.15, Math.min(10.0, scale[0] * delta));
            full.setScaleX(scale[0]);
            full.setScaleY(scale[0]);
        });

        // Toolbar tr\u00ean c\u00f9ng
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: rgba(0,0,0,0.75);");

        String btnStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 13px;";

        Button zoomInBtn  = new Button("\ud83d\udd0d +");   zoomInBtn.setStyle(btnStyle);
        Button zoomOutBtn = new Button("\ud83d\udd0d \u2212");   zoomOutBtn.setStyle(btnStyle);
        Button resetBtn   = new Button("\u21ba Reset"); resetBtn.setStyle(btnStyle);
        Button dlBtn      = new Button("\u2b07 T\u1ea3i v\u1ec1"); dlBtn.setStyle(btnStyle);

        zoomInBtn.setOnAction(e  -> { scale[0] = Math.min(10.0, scale[0] * 1.2); full.setScaleX(scale[0]); full.setScaleY(scale[0]); });
        zoomOutBtn.setOnAction(e -> { scale[0] = Math.max(0.15, scale[0] / 1.2); full.setScaleX(scale[0]); full.setScaleY(scale[0]); });
        resetBtn.setOnAction(e   -> { scale[0] = 1.0; full.setScaleX(1.0); full.setScaleY(1.0); });
        dlBtn.setOnAction(e      -> downloadViewedImage(imageSource, viewer));

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("\u2715 \u0110\u00f3ng");
        closeBtn.setStyle("-fx-background-color: rgba(229,72,77,0.88); -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 6 14; -fx-cursor: hand; -fx-font-size: 13px; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> viewer.close());

        toolbar.getChildren().addAll(zoomOutBtn, resetBtn, zoomInBtn, dlBtn, spacer, closeBtn);
        StackPane.setAlignment(toolbar, Pos.TOP_CENTER);
        root.getChildren().addAll(full, toolbar);

        viewer.setScene(new Scene(root, 900, 680));
        viewer.setMaximized(true);
        viewer.show();
    }

    private void downloadViewedImage(String imageSource, Stage owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("L\u01b0u \u1ea3nh");
        fc.setInitialFileName("moji_" + System.currentTimeMillis() + ".png");
        fc.getExtensionFilters().addAll(
            new ExtensionFilter("PNG Image", "*.png"),
            new ExtensionFilter("JPEG Image", "*.jpg")
        );
        File target = fc.showSaveDialog(owner);
        if (target == null) return;
        new Thread(() -> {
            try {
                if (imageSource.startsWith("data:image")) {
                    String b64 = imageSource.substring(imageSource.indexOf(',') + 1);
                    byte[] bytes = Base64.getDecoder().decode(b64);
                    Files.write(target.toPath(), bytes);
                    Platform.runLater(() -> callback.showAlert("\u0110\u00e3 l\u01b0u \u1ea3nh", "\u1ea2nh \u0111\u00e3 l\u01b0u t\u1ea1i:\n" + target.getAbsolutePath()));
                } else {
                    Platform.runLater(() -> callback.showAlert("L\u01b0u \u1ea3nh", "Kh\u00f4ng th\u1ec3 t\u1ea3i \u1ea3nh t\u1eeb URL n\u00e0y. Vui l\u00f2ng ch\u1ee5p m\u00e0n h\u00ecnh th\u1ee7 c\u00f4ng."));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> callback.showAlert("L\u1ed7i", "Kh\u00f4ng th\u1ec3 l\u01b0u \u1ea3nh: " + ex.getMessage()));
            }
        }, "img-dl").start();
    }

    private void downloadFileMessage(String content) {
        JsonObject meta = parseJsonObject(content);
        if (meta == null || !meta.has("data")) {
            callback.showAlert("File", "Khong co du lieu file de tai.");
            return;
        }
        try {
            String fileName = meta.has("fileName") ? meta.get("fileName").getAsString() : "file";
            byte[] bytes = Base64.getDecoder().decode(meta.get("data").getAsString());
            Path downloads = Path.of(System.getProperty("user.home"), "Downloads", "MojiMessenger");
            Files.createDirectories(downloads);
            Path target = uniqueDownloadPath(downloads, fileName);
            Files.write(target, bytes);
            callback.showAlert("Da tai file", "File da duoc luu tai:\n" + target);
        } catch (Exception ex) {
            callback.showAlert("Loi tai file", ex.getMessage());
        }
    }

    private Path uniqueDownloadPath(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target)) return target;
        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            target = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(target)) return target;
        }
        return dir.resolve(System.currentTimeMillis() + "_" + fileName);
    }

    private JsonObject parseJsonObject(String content) {
        if (content == null || !content.trim().startsWith("{")) return null;
        try {
            JsonElement parsed = JsonParser.parseString(content);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException ex) {
            return null;
        }
    }

    private String previewForMessage(String messageType, String content) {
        if ("IMAGE".equals(messageType)) {
            if (content != null && content.startsWith("[ICON:")) return "\uD83D\uDDBC Icon";
            return "\uD83D\uDDBC \u1EA2nh";
        }
        if ("FILE".equals(messageType)) {
            JsonObject meta = parseJsonObject(content);
            String fileName = meta != null && meta.has("fileName") ? meta.get("fileName").getAsString() : "file";
            return "\uD83D\uDCCE " + fileName;
        }
        if (content != null && content.startsWith("[STICKER:")) return "\uD83C\uDF9F Sticker";
        return content != null ? content : "";
    }

    /** Icon trạng thái: ✓ xám (SENT), ✓✓ xám (DELIVERED), ✓✓ xanh (SEEN) */
    private String getStatusIcon(String status) {
        if ("SEEN".equals(status)) return "\u2713\u2713";
        if ("DELIVERED".equals(status)) return "\u2713\u2713";
        return "\u2713";
    }

    /**
     * Tạo avatar: nếu có avatarUrl thì load ảnh (hình tròn),
     * nếu không thì hiển thị chữ viết tắt.
     */
    private StackPane makeAvatar(String initials, Color color, double size, boolean online, String avatarUrl) {
        StackPane sp = new StackPane();
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                ImageView iv = new ImageView(new Image(avatarUrl, size, size, true, true, true));
                iv.setFitWidth(size);
                iv.setFitHeight(size);
                Circle clip = new Circle(size / 2, size / 2, size / 2);
                iv.setClip(clip);
                sp.getChildren().add(iv);
            } catch (Exception e) {
                sp.getChildren().add(avatarCircle(initials, color, size));
            }
        } else {
            sp.getChildren().add(avatarCircle(initials, color, size));
        }

        // Chấm online xanh
        if (online) {
            Circle dot = new Circle(size * 0.14, Color.web("#31D158"));
            dot.setStroke(Color.web("#1B1E2B"));
            dot.setStrokeWidth(1.5);
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(dot, new Insets(0, size * 0.04, size * 0.04, 0));
            sp.getChildren().add(dot);
        }
        return sp;
    }

    /** Vòng tròn chữ viết tắt (fallback khi không có ảnh) */
    private StackPane avatarCircle(String initials, Color color, double size) {
        Circle bg = new Circle(size / 2, color);
        Label label = new Label(initials);
        label.setFont(Font.font("SansSerif", FontWeight.BOLD, size * 0.38));
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-smoothing-type: lcd;");
        return new StackPane(bg, label);
    }

    /** Giữ lại avatar() cũ để tương thích ngược */
    private StackPane avatar(String initials, Color color, double size, boolean online) {
        return makeAvatar(initials, color, size, online, null);
    }

    private void sendRx(long mid, String rx) {
        if (active == null) return;
        JsonObject d = new JsonObject();
        if (active.isGroup) d.addProperty("groupId", active.uid);
        else d.addProperty("receiverId", active.uid);
        d.addProperty("messageId", mid);
        d.addProperty("reaction", rx);
        client.send(Protocol.TYPE_REACTION, d);
    }
    private void unsend(long mid, HBox row) { JsonObject d=new JsonObject(); d.addProperty("receiverId",active!=null?active.uid:0); d.addProperty("messageId",mid); client.send(Protocol.TYPE_UNSEND,d); msgArea.getChildren().remove(row); }

    private void send() {
        String tx=input.getText().trim(); if(tx.isEmpty()||active==null) return; long ts=System.currentTimeMillis();
        JsonObject msgData = new JsonObject();
        if(active.isGroup) {
            msgData.addProperty("groupId",active.uid);
        } else {
            msgData.addProperty("receiverId",active.uid);
        }
        msgData.addProperty("content",tx);
        // Thêm replyToId nếu có
        if (replyToMsgId > 0) {
            msgData.addProperty("replyToId", replyToMsgId);
            msgData.addProperty("replyToContent", replyToContent);
            msgData.addProperty("replyToSender", replyToSender);
        }

        if(active.isGroup) {
            client.send(Protocol.TYPE_GROUP_MESSAGE, msgData);
        } else {
            client.send(Protocol.TYPE_MESSAGE, msgData);
        }
        bubble("B\u1EA1n",tx,ts,true,active!=null?active.uid:0);
        active.lastMsg=tx; active.time=timeFmt.format(new Date(ts)); listView.refresh(); input.clear();
        cancelReply();
    }

    private void showCreateGroup() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("T\u1EA1o nh\u00F3m m\u1EDBi"); d.setHeaderText("T\u1EA1o nh\u00F3m chat m\u1EDBi"); d.setContentText("T\u00EAn nh\u00F3m:");
        d.showAndWait().ifPresent(n -> {
            if (!n.trim().isEmpty()) {
                JsonObject dd = new JsonObject();
                dd.addProperty("name", n.trim());
                client.send(Protocol.TYPE_GROUP_CREATE, dd);
            }
        });
    }

    private void chooseFile() {
        FileChooser fc=new FileChooser(); fc.setTitle("Ch\u1ECDn file \u0111\u1EC3 g\u1EEDi");
        File f=fc.showOpenDialog(client.getPrimaryStage());
        if(f!=null) new Thread(()->sendFile(f)).start();
    }

    private void chooseImage() {
        FileChooser fc = new FileChooser(); fc.setTitle("Ch\u1ECDn \u1EA3nh \u0111\u1EC3 g\u1EEDi");
        fc.getExtensionFilters().add(new ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(client.getPrimaryStage());
        if (f != null) new Thread(() -> sendImage(f)).start();
    }

    /** G\u1EEDi \u1EA3nh d\u1EA1ng base64 data URI inline */
    private void sendImage(File f) {
        try {
            ConvItem target = active;
            if (target == null) return;
            String ext = getFileExtension(f.getName()).toLowerCase();
            String mime = ext.equals("jpg") || ext.equals("jpeg") ? "image/jpeg"
                        : ext.equals("gif") ? "image/gif"
                        : ext.equals("webp") ? "image/webp"
                        : "image/png";
            byte[] bytes = readAllBytes(f);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String dataUri = "data:" + mime + ";base64," + b64;

            long ts = System.currentTimeMillis();
            JsonObject msgData = new JsonObject();
            if (target.isGroup) {
                msgData.addProperty("groupId", target.uid);
            } else {
                msgData.addProperty("receiverId", target.uid);
            }
            msgData.addProperty("content", dataUri);
            msgData.addProperty("messageType", "IMAGE");
            client.send(target.isGroup ? Protocol.TYPE_GROUP_MESSAGE : Protocol.TYPE_MESSAGE, msgData);
            Platform.runLater(() -> {
                bubble("B\u1EA1n", dataUri, ts, true, target.uid, "IMAGE", "SENT", ts);
                target.lastMsg = previewForMessage("IMAGE", dataUri);
                target.time = timeFmt.format(new Date(ts));
                listView.refresh();
            });
        } catch (IOException e) {
            Platform.runLater(() -> callback.showAlert("L\u1ED7i", e.getMessage()));
        }
    }

    /** Gui file nhu mot message co metadata de luu duoc vao history. */
    private void sendFile(File f) {
        try {
            ConvItem target = active;
            if (target == null) return;
            String fid = System.currentTimeMillis() + "_" + f.getName();
            long fsz = f.length();
            String b64 = Base64.getEncoder().encodeToString(readAllBytes(f));
            JsonObject meta = new JsonObject();
            meta.addProperty("fileId", fid);
            meta.addProperty("fileName", f.getName());
            meta.addProperty("fileSize", fsz);
            meta.addProperty("data", b64);
            meta.addProperty("mime", Files.probeContentType(f.toPath()) != null ? Files.probeContentType(f.toPath()) : "application/octet-stream");

            JsonObject msgData = new JsonObject();
            if (target.isGroup) {
                msgData.addProperty("groupId", target.uid);
            } else {
                msgData.addProperty("receiverId", target.uid);
            }
            msgData.addProperty("content", gson.toJson(meta));
            msgData.addProperty("messageType", "FILE");
            client.send(target.isGroup ? Protocol.TYPE_GROUP_MESSAGE : Protocol.TYPE_MESSAGE, msgData);
            Platform.runLater(() -> {
                bubble("B\u1EA1n", gson.toJson(meta), System.currentTimeMillis(), true, target.uid, "FILE", "SENT", System.currentTimeMillis());
                target.lastMsg = previewForMessage("FILE", gson.toJson(meta));
                target.time = timeFmt.format(new Date());
                listView.refresh();
            });
        } catch (IOException e) {
            Platform.runLater(() -> callback.showAlert("L\u1ED7i", e.getMessage()));
        }
    }

    /** Hi\u1EC3n th\u1ECB bubble file v\u1EDBi icon + t\u00EAn + dung l\u01B0\u1EE3ng */
    private void showFileBubble(String fileName, long fileSize, String fileId) {
        HBox row = new HBox(8);
        row.setPadding(new Insets(2, 14, 4, 14));

        VBox bubbleBox = new VBox(6);
        bubbleBox.setPadding(new Insets(8, 12, 8, 12));
        bubbleBox.setMaxWidth(320);
        bubbleBox.getStyleClass().add("bubble-mine");

        Label fileIcon = new Label("\uD83D\uDCCE");
        fileIcon.setFont(Font.font(32));
        Label nameLbl = new Label(fileName);
        nameLbl.getStyleClass().add("file-name-label");
        nameLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        nameLbl.setTextFill(Color.WHITE);
        Label sizeLbl = new Label(fmtSz(fileSize));
        sizeLbl.setFont(Font.font("SansSerif", 11));
        sizeLbl.setTextFill(Color.rgb(200, 200, 200));

        HBox fileInfo = new HBox(10, fileIcon, new VBox(2, nameLbl, sizeLbl));
        fileInfo.setAlignment(Pos.CENTER_LEFT);
        bubbleBox.getChildren().add(fileInfo);

        long ts = System.currentTimeMillis();
        HBox statusRow = new HBox(4);
        statusRow.setAlignment(Pos.CENTER_RIGHT);
        Label timeLbl = new Label(timeFmt.format(new Date(ts)));
        timeLbl.getStyleClass().add("bubble-time-mine");
        Label statusIcon = new Label(getStatusIcon("SENT"));
        statusIcon.getStyleClass().add("bubble-status-icon");
        statusRow.getChildren().addAll(timeLbl, statusIcon);
        bubbleBox.getChildren().add(statusRow);

        row.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().add(bubbleBox);
        msgArea.getChildren().add(row);
        Platform.runLater(() -> msgScroll.setVvalue(1.0));
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

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "png";
    }

    private String fmtSz(long sz) { if(sz<1024)return sz+" B"; if(sz<1048576)return String.format("%.1f KB",sz/1024.0); return String.format("%.1f MB",sz/1048576.0); }

    public void addCallHistoryBubble(boolean isVideo, boolean missed, long durationSec) {
        if (active == null) return;
        Platform.runLater(() -> {
            msgArea.getChildren().removeIf(n -> n.getStyleClass().contains("chat-empty-state"));
            String icon = missed ? "📵" : (isVideo ? "📹" : "📞");
            String text;
            if (missed) {
                text = icon + "  Cuộc gọi nhỡ";
            } else {
                text = icon + "  " + (isVideo ? "Cuộc gọi video" : "Cuộc gọi thoại")
                        + " · " + String.format("%02d:%02d", durationSec / 60, durationSec % 60);
            }
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(6, 14, 6, 14));

            Label callLbl = new Label(text);
            callLbl.getStyleClass().add(missed ? "call-bubble-missed" : "call-bubble-ended");
            callLbl.setPadding(new Insets(7, 18, 7, 18));

            Label timeLbl = new Label("  " + timeFmt.format(new java.util.Date()));
            timeLbl.getStyleClass().add("bubble-time-theirs");

            HBox pill = new HBox(0, callLbl, timeLbl);
            pill.setAlignment(Pos.CENTER_LEFT);
            pill.setStyle("-fx-background-color: rgba(128,128,128,0.1); -fx-background-radius: 20; -fx-padding: 0 8 0 0;");
            row.getChildren().add(pill);
            msgArea.getChildren().add(row);
            Platform.runLater(() -> msgScroll.setVvalue(1.0));
        });
    }

    private VBox buildChatEmptyState() {
        VBox box = new VBox(14);
        box.getStyleClass().add("chat-empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(70, 40, 40, 40));
        box.setMaxWidth(Double.MAX_VALUE);
        Label icon = new Label("💬");
        icon.setFont(Font.font(52));
        Label title = new Label("Moji Messenger");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        title.getStyleClass().add("chat-empty-title");
        Label hint = new Label("Chọn một cuộc trò chuyện để bắt đầu\nhoặc tìm kiếm người dùng mới");
        hint.getStyleClass().add("chat-empty-hint");
        hint.setAlignment(Pos.CENTER);
        hint.setWrapText(true);
        hint.setMaxWidth(300);
        box.getChildren().addAll(icon, title, hint);
        return box;
    }

    private VBox buildNoMessageState() {
        VBox box = new VBox(12);
        box.getStyleClass().add("chat-empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 40, 40, 40));
        box.setMaxWidth(Double.MAX_VALUE);
        Label icon = new Label("✉️");
        icon.setFont(Font.font(44));
        Label hint = new Label("Chưa có tin nhắn nào\nHãy gửi tin nhắn đầu tiên!");
        hint.getStyleClass().add("chat-empty-hint");
        hint.setAlignment(Pos.CENTER);
        hint.setWrapText(true);
        box.getChildren().addAll(icon, hint);
        return box;
    }

    private void setTT(javafx.scene.Node n, String t) {
        if(t==null||t.isEmpty()) return;
        Tooltip tt = new Tooltip(t); tt.setStyle("-fx-background-color: #2C2F45; -fx-text-fill: #E0E0E0; -fx-background-radius: 6; -fx-padding: 6 10; -fx-font-size: 12px;");
        Tooltip.install(n, tt);
    }

    // --- ConvCell ---
    private class ConvCell extends ListCell<ConvItem> {
        private final StackPane av = new StackPane();
        private final Label nl=new Label(), ml=new Label(), tl=new Label(), ub=new Label();
        private final HBox cell;
        private final Label menuBtn;
        ConvCell() {
            cell=new HBox(12); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(10,14,10,14));
            cell.getStyleClass().add("conv-cell");
            VBox tc=new VBox(4); HBox tr=new HBox(); tr.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(nl,Priority.ALWAYS); tr.getChildren().addAll(nl,tl);
            HBox br=new HBox(6); br.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(ml,Priority.ALWAYS); ub.getStyleClass().add("unread-badge"); ub.setVisible(false); br.getChildren().addAll(ml,ub);
            tc.getChildren().addAll(tr,br);
            menuBtn = new Label("\u22EE"); menuBtn.setFont(Font.font(18)); menuBtn.setVisible(false);
            menuBtn.setStyle("-fx-text-fill: #888; -fx-cursor: hand; -fx-padding: 0 4;");
            cell.getChildren().addAll(av, tc, menuBtn);
            HBox.setHgrow(tc, Priority.ALWAYS);
            nl.setFont(Font.font("SansSerif",FontWeight.BOLD,14)); ml.setFont(Font.font("SansSerif",12)); tl.setFont(Font.font("SansSerif",11));
            nl.getStyleClass().add("conv-name");
            ml.getStyleClass().add("conv-msg");
            tl.getStyleClass().add("conv-time");
            selectedProperty().addListener((obs, wasSelected, isSelected) -> updateSelectionStyle());
            cell.setOnMouseEntered(e -> {
                if (!cell.getStyleClass().contains("conv-cell-hover")) cell.getStyleClass().add("conv-cell-hover");
                menuBtn.setVisible(true);
            });
            cell.setOnMouseExited(e -> {
                cell.getStyleClass().remove("conv-cell-hover");
                menuBtn.setVisible(false);
            });
        }
        protected void updateItem(ConvItem c, boolean e) {
            super.updateItem(c,e);
            if(e||c==null){ setGraphic(null); return; }
            updateSelectionStyle();
            av.getChildren().clear(); av.getChildren().add(makeAvatar(c.initials,c.avatarColor,44,c.online,c.avatarUrl));
            nl.setText(c.name);
            ml.setText(c.lastMsg!=null&&c.lastMsg.length()>28?c.lastMsg.substring(0,26)+"\u2026":c.lastMsg!=null?c.lastMsg:"");
            tl.setText(c.time!=null?c.time:"");
            if(c.unread>0){ ub.setText(String.valueOf(c.unread)); ub.setVisible(true); } else ub.setVisible(false);
            // Hover menu
            menuBtn.setVisible(false);
            menuBtn.setOnMouseClicked(ev -> {
                ev.consume();
                showConvMenu(c, ev.getScreenX(), ev.getScreenY());
            });
            setGraphic(cell);
        }

        private void updateSelectionStyle() {
            cell.getStyleClass().removeAll("conv-cell-selected");
            if (isSelected()) cell.getStyleClass().add("conv-cell-selected");
        }
    }

    // ==================== Group Info & Management ====================

    /** Yêu cầu thông tin nhóm từ server */
    private void showGroupInfo(long groupId) {
        JsonObject d = new JsonObject();
        d.addProperty("groupId", groupId);
        client.send(Protocol.TYPE_GROUP_INFO, d);
    }

    /** Mở dialog thông tin nhóm (gọi từ handleSuccess khi có data groups) */
    private void openGroupInfoDialog(JsonObject data) {
        long gid = data.get("id").getAsLong();
        String name = data.has("name") ? data.get("name").getAsString() : "";
        String desc = data.has("description") ? data.get("description").getAsString() : "";
        String avatarUrl = data.has("avatarUrl") ? data.get("avatarUrl").getAsString() : "";
        long ownerId = data.has("ownerId") ? data.get("ownerId").getAsLong() : -1;
        JsonArray members = data.getAsJsonArray("members");

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Thông tin nhóm");
        dialog.setMinWidth(380);
        dialog.setMinHeight(420);

        VBox root = new VBox(0);
        root.getStyleClass().add("group-info-root");
        root.setStyle("-fx-background-color: #181C2C; -fx-padding: 20;");

        // Header: avatar + tên + mô tả
        StackPane av = makeAvatar(
            name.length()>=2?name.substring(0,2).toUpperCase():name.substring(0,1).toUpperCase(),
            COLORS[(int)(Math.abs(gid) % COLORS.length)], 64, false, avatarUrl);
        av.setPadding(new Insets(0,0,10,0));

        Label nameLbl = new Label(name);
        nameLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
        nameLbl.setTextFill(Color.WHITE);
        nameLbl.setAlignment(Pos.CENTER);
        nameLbl.setMaxWidth(Double.MAX_VALUE);

        Label descLbl = new Label(desc != null && !desc.isEmpty() ? desc : "Chưa có mô tả");
        descLbl.setFont(Font.font("SansSerif", 12));
        descLbl.setTextFill(Color.rgb(140, 146, 172));
        descLbl.setWrapText(true);
        descLbl.setAlignment(Pos.CENTER);
        descLbl.setMaxWidth(Double.MAX_VALUE);
        descLbl.setPadding(new Insets(4,0,10,0));

        // Member list header
        HBox memberHdr = new HBox(8);
        memberHdr.setAlignment(Pos.CENTER_LEFT);
        memberHdr.setPadding(new Insets(12,0,8,0));
        Label mlbl = new Label("Thành viên (" + members.size() + ")");
        mlbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        mlbl.setTextFill(Color.WHITE);
        HBox.setHgrow(mlbl, Priority.ALWAYS);
        memberHdr.getChildren().add(mlbl);

        // Nút Add Member (chỉ admin)
        boolean isCurrentUserAdmin = false;
        for (JsonElement e : members) {
            JsonObject m = e.getAsJsonObject();
            if (m.get("userId").getAsLong() == currentUser.getId() && "ADMIN".equals(m.get("role").getAsString())) {
                isCurrentUserAdmin = true; break;
            }
        }

        Button addBtn = new Button("+ Thêm");
        addBtn.getStyleClass().add("button-primary");
        addBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4 12;");
        addBtn.setVisible(isCurrentUserAdmin);
        addBtn.setManaged(isCurrentUserAdmin);
        addBtn.setOnAction(e -> {
            dialog.close();
            addMemberToGroup(gid, name);
        });
        memberHdr.getChildren().add(addBtn);

        // Member list
        VBox memberList = new VBox(4);
        for (JsonElement e : members) {
            JsonObject m = e.getAsJsonObject();
            long uid = m.get("userId").getAsLong();
            String dn = m.has("displayName") ? m.get("displayName").getAsString() : "";
            String un = m.has("username") ? m.get("username").getAsString() : "";
            String role = m.has("role") ? m.get("role").getAsString() : "MEMBER";
            String mav = m.has("avatarUrl") ? m.get("avatarUrl").getAsString() : "";

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6,8,6,8));
            row.getStyleClass().add("member-row");

            Color ac = COLORS[(int)(Math.abs(uid) % COLORS.length)];
            StackPane av2 = makeAvatar(
                dn.length()>=2?dn.substring(0,2).toUpperCase():dn.substring(0,1).toUpperCase(),
                ac, 36, false, mav);

            VBox info = new VBox(2);
            Label nl = new Label(dn);
            nl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
            nl.setTextFill(Color.WHITE);
            Label sl = new Label("ADMIN".equals(role) ? "Quản trị viên" : "Thành viên");
            sl.setFont(Font.font("SansSerif", 11));
            sl.setTextFill(Color.rgb(140, 146, 172));
            info.getChildren().addAll(nl, sl);
            HBox.setHgrow(info, Priority.ALWAYS);

            // Add avatar + info first
            row.getChildren().addAll(av2, info);

            // Action buttons (chỉ admin mới thấy, không áp dụng cho chính admin)
            if (isCurrentUserAdmin && uid != currentUser.getId()) {
                if ("ADMIN".equals(role)) {
                    Button demoteBtn = new Button("Hạ");
                    demoteBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;");
                    demoteBtn.setOnAction(ev -> {
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", gid);
                        req.addProperty("targetUserId", uid);
                        client.send(Protocol.TYPE_GROUP_DEMOTE, req);
                        dialog.close();
                    });
                    row.getChildren().add(demoteBtn);
                } else {
                    HBox actionBtns = new HBox(4);
                    Button promoteBtn = new Button("Lên QTV");
                    promoteBtn.setStyle("-fx-background-color: #0068FF; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;");
                    promoteBtn.setOnAction(ev -> {
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", gid);
                        req.addProperty("targetUserId", uid);
                        client.send(Protocol.TYPE_GROUP_PROMOTE, req);
                        dialog.close();
                    });
                    Button kickBtn = new Button("Xóa");
                    kickBtn.setStyle("-fx-background-color: #E5484D; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;");
                    kickBtn.setOnAction(ev -> {
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", gid);
                        req.addProperty("targetUserId", uid);
                        client.send(Protocol.TYPE_GROUP_REMOVE_MEMBER, req);
                        dialog.close();
                    });
                    actionBtns.getChildren().addAll(promoteBtn, kickBtn);
                    row.getChildren().add(actionBtns);
                }
            } else if (uid == currentUser.getId()) {
                Label meLbl = new Label("(Bạn)");
                meLbl.setFont(Font.font("SansSerif", 11));
                meLbl.setTextFill(Color.rgb(0, 132, 255));
                row.getChildren().add(meLbl);
            }

            memberList.getChildren().add(row);
        }

        ScrollPane memberScroll = new ScrollPane(memberList);
        memberScroll.setFitToWidth(true);
        memberScroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(memberScroll, Priority.ALWAYS);

        // Footer buttons
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(12,0,0,0));

        Button editBtn = new Button("\u270F\uFE0F Sửa tên & mô tả");
        editBtn.setStyle("-fx-background-color: #2A2F45; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 14; -fx-background-radius: 16;");
        editBtn.setOnAction(e -> {
            TextInputDialog nameDlg = new TextInputDialog(name);
            nameDlg.setTitle("Sửa nhóm"); nameDlg.setHeaderText("Tên nhóm mới:");
            nameDlg.showAndWait().ifPresent(newName -> {
                if (!newName.trim().isEmpty()) {
                    TextInputDialog descDlg = new TextInputDialog(desc);
                    descDlg.setTitle("Sửa nhóm"); descDlg.setHeaderText("Mô tả nhóm mới:");
                    descDlg.showAndWait().ifPresent(newDesc -> {
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", gid);
                        req.addProperty("name", newName.trim());
                        req.addProperty("description", newDesc.trim());
                        client.send(Protocol.TYPE_GROUP_UPDATE, req);
                        dialog.close();
                    });
                }
            });
        });
        footer.getChildren().add(editBtn);

        Button leaveBtn = new Button("\uD83D\uDEAA Rời nhóm");
        leaveBtn.setStyle("-fx-background-color: #E5484D; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 14; -fx-background-radius: 16;");
        leaveBtn.setOnAction(e -> {
            JsonObject d = new JsonObject();
            d.addProperty("groupId", gid);
            client.send(Protocol.TYPE_GROUP_LEAVE, d);
            removeConv(gid);
            dialog.close();
        });
        footer.getChildren().add(leaveBtn);

        root.getChildren().addAll(av, nameLbl, descLbl, memberHdr, memberScroll, footer);
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
    }

    /** Dialog thêm thành viên vào nhóm */
    private void addMemberToGroup(long groupId, String groupName) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Thêm thành viên vào " + groupName);
        dialog.setMinWidth(320);
        dialog.setMinHeight(300);

        VBox root = new VBox(12);
        root.setStyle("-fx-background-color: #181C2C; -fx-padding: 20;");

        Label title = new Label("Chọn bạn bè để thêm vào nhóm");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);

        TextField searchField = new TextField();
        searchField.setPromptText("Tìm kiếm bạn bè...");
        searchField.getStyleClass().add("sidebar-search");

        VBox friendList = new VBox(4);

        // Gửi request danh sách bạn bè
        client.send(Protocol.TYPE_FRIEND_LIST, new JsonObject());

        // Lắng nghe response tạm thời
        // Do không có observer pattern, dùng polling: sau 1 giây, parse friends từ ConvItem map
        new Thread(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ex) {}
            Platform.runLater(() -> {
                friendList.getChildren().clear();
                for (ConvItem c : listItems) {
                    if (c.isGroup || c.uid <= 0) continue;
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(6,8,6,8));
                    row.getStyleClass().add("contact-row");
                    row.setCursor(javafx.scene.Cursor.HAND);

                    StackPane av = makeAvatar(c.initials, c.avatarColor, 36, false, c.avatarUrl);
                    Label nl = new Label(c.name);
                    nl.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
                    nl.setTextFill(Color.WHITE);
                    HBox.setHgrow(nl, Priority.ALWAYS);

                    Button addBtn = new Button("+ Thêm");
                    addBtn.getStyleClass().add("button-primary");
                    addBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 10; -fx-background-radius: 10;");
                    addBtn.setOnAction(ev -> {
                        // Kiểm tra xem đã là member chưa (gửi request)
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", groupId);
                        req.addProperty("targetUserId", c.uid);
                        // Dùng GROUP_JOIN để thêm (back end kiểm tra duplicate)
                        JsonObject joinReq = new JsonObject();
                        joinReq.addProperty("groupId", groupId);
                        joinReq.addProperty("userId", c.uid);
                        client.send(Protocol.TYPE_GROUP_JOIN, joinReq);
                        callback.showAlert("Thêm thành viên", "Đã gửi yêu cầu thêm " + c.name + " vào nhóm");
                        dialog.close();
                    });

                    row.getChildren().addAll(av, nl, addBtn);
                    friendList.getChildren().add(row);
                }
                if (friendList.getChildren().isEmpty()) {
                    Label empty = new Label("Không có bạn bè nào để thêm");
                    empty.setTextFill(Color.rgb(140, 146, 172));
                    friendList.getChildren().add(empty);
                }
            });
        }, "add-member-delay").start();

        Button closeBtn = new Button("Đóng");
        closeBtn.setStyle("-fx-background-color: #2A2F45; -fx-text-fill: white; -fx-padding: 6 20; -fx-background-radius: 16;");
        closeBtn.setOnAction(e -> dialog.close());

        ScrollPane scroll = new ScrollPane(friendList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(title, searchField, scroll, closeBtn);
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
    }

    private void showConvMenu(ConvItem c, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem("\u270F\uFE0F \u0110\u1ED5i t\u00EAn");
        rename.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog(c.name);
            d.setTitle("\u0110\u1ED5i t\u00EAn"); d.setHeaderText("\u0110\u1ED5i t\u00EAn cu\u1ED9c tr\u00F2 chuy\u1EC7n");
            d.showAndWait().ifPresent(n -> {
                if(!n.trim().isEmpty()) {
                    if (c.isGroup) {
                        JsonObject req = new JsonObject();
                        req.addProperty("groupId", c.uid);
                        req.addProperty("name", n.trim());
                        client.send(Protocol.TYPE_GROUP_UPDATE, req);
                    } else {
                        renameConv(c.uid, n.trim());
                    }
                }
            });
        });
        MenuItem pin = new MenuItem("\uD83D\uDCCC Ghim");
        pin.setOnAction(e -> pinConv(c.uid));

        MenuItem mute = new MenuItem("\uD83D\uDD07 T\u1EAFt th\u00F4ng b\u00E1o");
        mute.setOnAction(e -> callback.showAlert("Th\u00F4ng b\u00E1o", "\u0110\u00E3 t\u1EAFt th\u00F4ng b\u00E1o cho " + c.name));

        MenuItem delete = new MenuItem("\uD83D\uDDD1 X\u00F3a");
        delete.setOnAction(e -> {
            JsonObject req = new JsonObject();
            req.addProperty("conversationId", c.uid);
            client.send(Protocol.TYPE_CONVERSATION_DELETE, req);
            removeConv(c.uid);
        });

        menu.getItems().addAll(rename, pin, mute, new SeparatorMenuItem(), delete);

        if (c.isGroup) {
            MenuItem leave = new MenuItem("\uD83D\uDEAA R\u1EDDi nh\u00F3m");
            leave.setOnAction(e -> {
                JsonObject d = new JsonObject();
                d.addProperty("groupId", c.uid);
                client.send(Protocol.TYPE_GROUP_LEAVE, d);
                removeConv(c.uid);
            });
            menu.getItems().add(1, leave);
        }
        // Hi\u1EC7n menu t\u1EA1i v\u1ECB tr\u00ED c\u1ED1 \u0111\u1ECBnh s\u00E1t n\u00FAt "..." (t\u1ECDa \u0111\u1ED9 m\u00E0n h\u00ECnh)
        menu.show(listView.getScene().getWindow(), screenX, screenY);
    }
}
