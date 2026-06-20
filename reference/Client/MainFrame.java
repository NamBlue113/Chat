package Client;

import Client.model.ChatFile;
import Client.model.Message;
import Client.model.ChatItem;
import Client.model.UserSearchResult;
import Client.service.FileService;
import Client.service.FriendService;
import Client.service.MessageService;
import Client.service.SocketListener;
import Client.service.SocketService;
import Client.service.ChatService;
import Client.ui.ChatPanel;
import Client.ui.EmojiDialog;
import Client.ui.FriendPanel;
import Client.ui.VideoCallFrame;
import login_register.Session;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MainFrame extends JFrame {

    private String currentUser;

    private SocketService socketService;
    private ChatService chatService;
    private MessageService messageService;
    private FileService fileService;
    private FriendService friendService;

    private FriendPanel friendPanel;
    private ChatPanel chatPanel;

    private JLabel chatTitle;
    private JTextField messageField;

    private List<String> friends = new ArrayList<>();

    public MainFrame() {

        currentUser = Session.username;

        messageService = new MessageService();
        chatService = new ChatService();
        fileService = new FileService();
        friendService = new FriendService();

        initUI();
        initSocket();

        loadFriends();

        setVisible(true);
    }

    private void initUI() {

        setTitle("Chat App");
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel menuPanel = createMenuPanel();

        JPanel leftPanel = createLeftPanel();

        JPanel rightPanel = createRightPanel();

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        leftPanel,
                        rightPanel
                );

        split.setDividerLocation(300);

        JPanel container =
                new JPanel(new BorderLayout());

        container.add(menuPanel, BorderLayout.WEST);
        container.add(split, BorderLayout.CENTER);

        add(container);
    }

    private JPanel createMenuPanel() {

        JPanel menuPanel = new JPanel();

        menuPanel.setBackground(
                new Color(0, 120, 215)
        );

        menuPanel.setPreferredSize(
                new Dimension(70, 0)
        );

        menuPanel.setLayout(
                new BoxLayout(
                        menuPanel,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel avatar =
                new JLabel(
                        currentUser
                                .substring(0,1)
                                .toUpperCase()
                );

        avatar.setForeground(Color.WHITE);

        avatar.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        18
                )
        );

        avatar.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton btnMessage =
                new JButton("💬");

        JButton btnSetting =
                new JButton("⚙");

        styleMenuButton(btnMessage);
        styleMenuButton(btnSetting);

        btnMessage.addActionListener(e ->
                loadFriends()
        );
        btnSetting.addActionListener(e ->
                showSettingsDialog()
        );

        menuPanel.add(Box.createVerticalStrut(15));
        menuPanel.add(avatar);

        menuPanel.add(Box.createVerticalStrut(25));
        menuPanel.add(btnMessage);

        JButton btnFriend = new JButton("👥");
        styleMenuButton(btnFriend);

        menuPanel.add(Box.createVerticalStrut(10));
        menuPanel.add(btnFriend);

        menuPanel.add(Box.createVerticalGlue());

        menuPanel.add(btnSetting);
        menuPanel.add(Box.createVerticalStrut(15));

        return menuPanel;
    }

    private void styleMenuButton(JButton button) {

        button.setFocusPainted(false);

        button.setBorderPainted(false);

        button.setBackground(
                new Color(0, 120, 215)
        );

        button.setForeground(Color.WHITE);

        button.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
    }

    private JPanel createLeftPanel() {

        JPanel leftPanel =
                new JPanel(new BorderLayout());

        leftPanel.setPreferredSize(
                new Dimension(300, 0)
        );

        JTextField searchField =
                new JTextField();

        searchField.setPreferredSize(
                new Dimension(0, 40)
        );

        JButton addFriendBtn =
                new JButton("+");

        addFriendBtn.setPreferredSize(
                new Dimension(50, 40)
        );

        JPanel topPanel =
                new JPanel(new BorderLayout());

        topPanel.add(
                searchField,
                BorderLayout.CENTER
        );

        topPanel.add(
                addFriendBtn,
                BorderLayout.EAST
        );

        friendPanel =
                new FriendPanel();

        leftPanel.add(
                topPanel,
                BorderLayout.NORTH
        );

        leftPanel.add(
                friendPanel,
                BorderLayout.CENTER
        );

        searchField.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {

                            public void insertUpdate(
                                    javax.swing.event.DocumentEvent e) {

                                friendPanel.loadFriends(
                                        friends,
                                        searchField.getText(),
                                        MainFrame.this::openConversation
                                );
                            }

                            public void removeUpdate(
                                    javax.swing.event.DocumentEvent e) {

                                friendPanel.loadFriends(
                                        friends,
                                        searchField.getText(),
                                        MainFrame.this::openConversation
                                );
                            }

                            public void changedUpdate(
                                    javax.swing.event.DocumentEvent e) {

                                friendPanel.loadFriends(
                                        friends,
                                        searchField.getText(),
                                        MainFrame.this::openConversation
                                );
                            }
                        }
                );

        addFriendBtn.addActionListener(e ->
                showAddFriendDialog()
        );

        return leftPanel;
    }

    private JPanel createRightPanel() {

        JPanel rightPanel =
                new JPanel(new BorderLayout());

        JPanel header =
                new JPanel(new BorderLayout());

        header.setPreferredSize(
                new Dimension(0, 70)
        );

        chatTitle =
                new JLabel("Chọn người để chat");

        chatTitle.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        22
                )
        );

        header.add(
                chatTitle,
                BorderLayout.WEST
        );

        rightPanel.add(
                header,
                BorderLayout.NORTH
        );

        chatPanel =
                new ChatPanel();

        rightPanel.add(
                chatPanel,
                BorderLayout.CENTER
        );

        JPanel footer =
                new JPanel(new BorderLayout());

        footer.setPreferredSize(
                new Dimension(0, 60)
        );

        JButton emojiBtn = new JButton("😀");
        JButton sendButton = new JButton("Gửi");
        JButton fileButton = new JButton("📎");
        JButton videoButton = new JButton("📹");

        messageField =
                new JTextField();

        footer.add(
                emojiBtn,
                BorderLayout.WEST
        );

        footer.add(
                messageField,
                BorderLayout.CENTER
        );

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                5,
                                5
                        )
                );

        buttonPanel.add(videoButton);
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);

        footer.add(
                buttonPanel,
                BorderLayout.EAST
        );

        rightPanel.add(
                footer,
                BorderLayout.SOUTH
        );

        emojiBtn.addActionListener(e ->

                new EmojiDialog(
                        this,
                        emoji -> messageField.setText(
                                messageField.getText()
                                        + emoji
                        )
                ).setVisible(true)
        );

        sendButton.addActionListener(e ->
                sendCurrentMessage()
        );

        fileButton.addActionListener(e ->
                chooseAndSendFile()
        );

        videoButton.addActionListener(e -> {

            if(chatTitle.getText().equals("Chọn người để chat")){

                JOptionPane.showMessageDialog(
                        this,
                        "Hãy chọn người để gọi"
                );

                return;
            }

            socketService.sendCall(
                    chatTitle.getText()
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Đang gọi..."
            );
        });

        return rightPanel;
    }

    private void loadFriends() {

        friends =
                friendService.getFriends(
                        currentUser
                );

        friendPanel.loadFriends(
                friends,
                "",
                this::openConversation
        );
    }

    private void openConversation(String friend) {

        chatTitle.setText(friend);

        chatPanel.clearMessages();

        for (ChatItem item :
                chatService.getConversation(
                        currentUser,
                        friend
                )) {

            if(item.getType().equals("MESSAGE")){

                chatPanel.addMessageBubble(
                        item.getContent(),
                        item.getSender().equals(currentUser),
                        item.getSentTime()
                );
            }

            else if(item.getType().equals("FILE")){

                chatPanel.addFileBubble(
                        item.getSender().equals(currentUser),
                        item.getFileName(),
                        Base64.getEncoder()
                                .encodeToString(
                                        item.getFileData()
                                ),
                        item.getSentTime()
                );
            }
        }
    }

    private void initSocket() {

        socketService =
                new SocketService(currentUser);

        socketService.setListener(
                new SocketListener() {

                    @Override
                    public void onMessageReceived(
                            String sender,
                            String content) {

                        SwingUtilities.invokeLater(() -> {

                            if (sender.equals(
                                    chatTitle.getText()
                            )) {

                                chatPanel.addMessageBubble(
                                        content,
                                        false,
                                        java.time.LocalTime.now()
                                                .format(
                                                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                                )
                                );
                            }
                        });
                    }

                    @Override
                    public void onFileReceived(
                            String sender,
                            String fileName,
                            byte[] data) {

                        SwingUtilities.invokeLater(() -> {

                            if (sender.equals(
                                    chatTitle.getText()
                            )) {

                                chatPanel.addFileBubble(
                                        false,
                                        fileName,
                                        Base64.getEncoder().encodeToString(data),
                                        java.time.LocalTime.now()
                                                .format(
                                                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                                )
                                );
                            }
                        });
                    }

                    @Override
                    public void onCallReceived(String sender) {

                        SwingUtilities.invokeLater(() -> {

                            int result =
                                    JOptionPane.showConfirmDialog(
                                            MainFrame.this,
                                            sender + " đang gọi cho bạn",
                                            "Cuộc gọi đến",
                                            JOptionPane.YES_NO_OPTION
                                    );

                            if(result == JOptionPane.YES_OPTION){

                                socketService.acceptCall(sender);

                                new VideoCallFrame(
                                        socketService,
                                        sender
                                );
                            }
                        });
                    }

                    @Override
                    public void onCallAccepted(String sender) {

                        SwingUtilities.invokeLater(() -> {

                            new VideoCallFrame(
                                    socketService,
                                    sender
                            );
                        });
                    }

                    @Override
                    public void onVideoFrame(
                            String sender,
                            byte[] imageData) {

                        VideoCallFrame.updateRemoteFrame(
                                imageData
                        );
                    }

                    @Override
                    public void onCallEnded(String sender) {

                        SwingUtilities.invokeLater(() -> {

                            VideoCallFrame.closeRemoteCall();

                            JOptionPane.showMessageDialog(
                                    MainFrame.this,
                                    sender + " đã kết thúc cuộc gọi"
                            );
                        });
                    }

                    @Override
                    public void onAudioFrame(
                            String sender,
                            byte[] audioData
                    ) {

                        VideoCallFrame.updateAudio(
                                audioData
                        );
                    }
                }
        );

        socketService.connect();
    }

    private void sendCurrentMessage() {

        if (chatTitle.getText().equals(
                "Chọn người để chat")) {

            JOptionPane.showMessageDialog(
                    this,
                    "Hãy chọn người nhận"
            );

            return;
        }

        String msg =
                messageField.getText().trim();

        if (msg.isEmpty()) {
            return;
        }

        chatPanel.addMessageBubble(
                msg,
                true,
                java.time.LocalTime.now()
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        )
        );

        messageService.saveMessage(
                currentUser,
                chatTitle.getText(),
                msg
        );

        socketService.sendMessage(
                chatTitle.getText(),
                msg
        );

        messageField.setText("");
    }

    private void chooseAndSendFile() {

        if (chatTitle.getText().equals(
                "Chọn người để chat")) {

            JOptionPane.showMessageDialog(
                    this,
                    "Hãy chọn người nhận"
            );

            return;
        }

        JFileChooser chooser =
                new JFileChooser();

        if (chooser.showOpenDialog(this)
                == JFileChooser.APPROVE_OPTION) {

            File file =
                    chooser.getSelectedFile();

            try {

                byte[] data =
                        Files.readAllBytes(
                                file.toPath()
                        );

                fileService.saveFile(
                        currentUser,
                        chatTitle.getText(),
                        file.getName(),
                        data
                );

                socketService.sendFile(
                        chatTitle.getText(),
                        file
                );

                chatPanel.addFileBubble(
                        true,
                        file.getName(),
                        Base64.getEncoder().encodeToString(data),
                        java.time.LocalTime.now()
                                .format(
                                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                )
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    private void showAddFriendDialog() {

        JDialog dialog =
                new JDialog(
                        this,
                        "Thêm bạn",
                        true
                );

        dialog.setSize(370, 470);

        dialog.setLocationRelativeTo(this);

        dialog.setLayout(
                new BorderLayout()
        );

        JPanel resultPanel =
                new JPanel();

        resultPanel.setLayout(
                new BoxLayout(
                        resultPanel,
                        BoxLayout.Y_AXIS
                )
        );

        JTextField txtSearch =
                new JTextField();

        JComboBox<String> typeBox =
                new JComboBox<>(
                        new String[]{
                                "Tên",
                                "Số điện thoại"
                        }
                );

        JButton btnSearch =
                new JButton("Tìm kiếm");

        JButton btnCancel =
                new JButton("Hủy");

        JPanel searchPanel =
                new JPanel(
                        new BorderLayout(10, 0)
                );

        searchPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        15, 15, 15, 15
                )
        );

        txtSearch.setPreferredSize(
                new Dimension(270, 30)
        );

        searchPanel.add(
                typeBox,
                BorderLayout.WEST
        );

        searchPanel.add(
                txtSearch,
                BorderLayout.CENTER
        );

        JScrollPane scroll =
                new JScrollPane(resultPanel);

        JPanel center =
                new JPanel(
                        new BorderLayout(10, 10)
                );

        center.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 20, 20
                )
        );

        center.add(
                searchPanel,
                BorderLayout.NORTH
        );

        center.add(
                scroll,
                BorderLayout.CENTER
        );

        dialog.add(
                center,
                BorderLayout.CENTER
        );

        JPanel bottom =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );

        bottom.add(btnCancel);
        bottom.add(btnSearch);

        dialog.add(
                bottom,
                BorderLayout.SOUTH
        );

        btnCancel.addActionListener(
                e -> dialog.dispose()
        );

        btnSearch.addActionListener(e -> {

            resultPanel.removeAll();

            String type =
                    typeBox.getSelectedItem()
                            .toString()
                            .equals("Số điện thoại")
                            ? "phone"
                            : "username";

            List<UserSearchResult> users =
                    friendService.searchUsers(
                            txtSearch.getText().trim(),
                            type,
                            currentUser
                    );

            if (users.isEmpty()) {

                resultPanel.add(
                        new JLabel(
                                "Không tìm thấy người dùng"
                        )
                );
            }

            for (UserSearchResult user : users) {

                JPanel row =
                        new JPanel(
                                new BorderLayout(15, 0)
                        );

                row.setMaximumSize(
                        new Dimension(
                                Integer.MAX_VALUE,
                                80
                        )
                );

                row.setBorder(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(
                                        new Color(230, 230, 230)
                                ),
                                BorderFactory.createEmptyBorder(
                                        10, 10, 10, 10
                                )
                        )
                );

                JLabel avatar =
                        new JLabel(
                                user.getUsername()
                                        .substring(0, 1)
                                        .toUpperCase(),
                                SwingConstants.CENTER
                        );

                avatar.setPreferredSize(
                        new Dimension(50, 50)
                );

                avatar.setOpaque(true);

                avatar.setBackground(
                        new Color(0, 120, 215)
                );

                avatar.setForeground(Color.WHITE);

                avatar.setFont(
                        new Font(
                                "Arial",
                                Font.BOLD,
                                20
                        )
                );

                JPanel info =
                        new JPanel(
                                new GridLayout(2, 1)
                        );

                info.add(
                        new JLabel(
                                user.getUsername()
                        )
                );

                info.add(
                        new JLabel(
                                user.getPhone()
                        )
                );

                JButton addBtn =
                        new JButton("Kết bạn");

                addBtn.setFocusPainted(false);

                addBtn.setBackground(
                        new Color(0, 120, 215)
                );

                addBtn.setForeground(Color.WHITE);

                addBtn.addActionListener(ev -> {

                    boolean success =
                            friendService.addFriend(
                                    currentUser,
                                    user.getUsername()
                            );

                    if (success) {

                        JOptionPane.showMessageDialog(
                                dialog,
                                "Kết bạn thành công"
                        );

                        loadFriends();

                    } else {

                        JOptionPane.showMessageDialog(
                                dialog,
                                "Đã là bạn bè hoặc không hợp lệ"
                        );
                    }
                });

                row.add(
                        avatar,
                        BorderLayout.WEST
                );

                row.add(
                        info,
                        BorderLayout.CENTER
                );

                row.add(
                        addBtn,
                        BorderLayout.EAST
                );

                resultPanel.add(row);
            }

            resultPanel.revalidate();
            resultPanel.repaint();
        });

        dialog.setVisible(true);
    }

    private void showSettingsDialog() {

        JDialog dialog =
                new JDialog(
                        this,
                        "Cài đặt tài khoản",
                        true
                );

        dialog.setSize(400,300);

        dialog.setLocationRelativeTo(this);

        dialog.setLayout(
                new GridLayout(5,2,10,10)
        );

        JTextField txtUsername =
                new JTextField(currentUser);

        JTextField txtEmail =
                new JTextField();

        JTextField txtPhone =
                new JTextField();

        JPasswordField txtPassword =
                new JPasswordField();

        JButton btnSave =
                new JButton("Lưu");

        dialog.add(new JLabel("Tên đăng nhập"));
        dialog.add(txtUsername);

        dialog.add(new JLabel("Email"));
        dialog.add(txtEmail);

        dialog.add(new JLabel("Số điện thoại"));
        dialog.add(txtPhone);

        dialog.add(new JLabel("Mật khẩu mới"));
        dialog.add(txtPassword);

        dialog.add(new JLabel());
        dialog.add(btnSave);

        btnSave.addActionListener(e -> {

            boolean ok =
                    friendService.updateAccount(
                            currentUser,
                            txtUsername.getText(),
                            txtEmail.getText(),
                            txtPhone.getText(),
                            String.valueOf(
                                    txtPassword.getPassword()
                            )
                    );

            if(ok){

                JOptionPane.showMessageDialog(
                        dialog,
                        "Cập nhật thành công"
                );

                dialog.dispose();

            }else{

                JOptionPane.showMessageDialog(
                        dialog,
                        "Cập nhật thất bại"
                );
            }
        });

        dialog.setVisible(true);
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(
                MainFrame::new
        );
    }
}








