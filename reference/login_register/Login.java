package login_register;

import database.Database;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Login extends JFrame {

    JTextField email = new JTextField();

    JTextField user = new JTextField();

    public Login() {

        setTitle("Đăng nhập");

        setSize(1200,700);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // ================= BACKGROUND =================

        BackgroundPanel background =
                new BackgroundPanel(
                        "images/bg.jpg"
                );

        background.setLayout(
                new GridBagLayout()
        );

        setContentPane(background);

        // ================= PANEL =================

        JPanel panel = new JPanel() {

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 =
                        (Graphics2D) g;

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                g2.setColor(
                        new Color(0,0,0,20)
                );

                g2.fillRoundRect(
                        0,
                        0,
                        getWidth(),
                        getHeight(),
                        30,
                        30
                );

                super.paintComponent(g);
            }
        };

        panel.setOpaque(false);

        panel.setPreferredSize(
                new Dimension(380,400)
        );

        panel.setLayout(null);

        // ================= TITLE =================

        JLabel title =
                new JLabel("Đăng nhập");

        title.setForeground(Color.WHITE);

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        22
                )
        );

        title.setBounds(
                130,
                40,
                200,
                30
        );

        // ================= EMAIL =================

        JLabel lbEmail = new JLabel("Email");

        lbEmail.setForeground(Color.WHITE);

        lbEmail.setBounds(
                50,
                90,
                300,
                20
        );

        email.setBounds(
                50,
                115,
                280,
                35
        );

        // ================= USERNAME =================

        JLabel LbOR = new JLabel(
                "----------------OR----------------");

        LbOR.setForeground(Color.WHITE);

        LbOR.setBounds(
                110,
                163,
                300,
                25
        );

        // ================= USERNAME =================

        JLabel lbUser =
                new JLabel("Tên tài khoản");

        lbUser.setForeground(Color.WHITE);

        lbUser.setBounds(
                50,
                185,
                300,
                20
        );

        user.setBounds(
                50,
                210,
                280,
                35
        );

        // ================= BUTTON LOGIN =================

        JButton loginBtn =
                new JButton("Tiếp theo");

        loginBtn.setBounds(
                50,
                280,
                280,
                35
        );

        loginBtn.setFocusPainted(false);

        loginBtn.setBorderPainted(false);

        loginBtn.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR
                )
        );

        // ================= BUTTON REGISTER =================

        JButton registerBtn =
                new JButton("Tạo tài khoản");

        registerBtn.setBounds(
                50,
                330,
                280,
                30
        );

        registerBtn.setFocusPainted(false);

        registerBtn.setBorderPainted(false);

        registerBtn.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR
                )
        );

        // ================= ADD =================

        panel.add(title);

        panel.add(lbEmail);
        panel.add(email);

        panel.add(LbOR);

        panel.add(lbUser);
        panel.add(user);

        panel.add(loginBtn);
        panel.add(registerBtn);

        background.add(panel);

        // ================= LOGIN EVENT =================

        loginBtn.addActionListener(e -> {

            loginCheck();
        });

        // ================= REGISTER EVENT =================

        registerBtn.addActionListener(e -> {

            new Register();

            dispose();
        });

        setVisible(true);
    }

    // ================= CHECK LOGIN =================

    void loginCheck(){

        String username = user.getText().trim();
        String emailText = email.getText().trim();

        if(username.isEmpty() && emailText.isEmpty()){

            JOptionPane.showMessageDialog(
                    this,
                    "Nhập emai hoặc tên tài khoản"
            );

            return;
        }

        try{

            Connection conn =
                    Database.connect();

            String sql =
                    "SELECT * FROM users WHERE username=? OR email=?";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(
                    1,
                    username);

            ps.setString(
                    2,
                    email.getText().trim());

            ResultSet rs =
                    ps.executeQuery();

            // ================= CHECK USER =================

            if(rs.next()){

                new PasswordForm(username);

                dispose();
            }

            else{

                JOptionPane.showMessageDialog(
                        this,
                        "Tài khoản không tồn tại"
                );
            }

        }catch(Exception e){

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Lỗi kết nối database"
            );
        }
    }

    public static void main(String[] args) {

        new Login();
    }
}