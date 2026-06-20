package login_register;

import database.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Register extends JFrame {

    JTextField email = new JTextField();

    JTextField phone = new JTextField();

    JTextField user = new JTextField();

    public Register(){

        setTitle("Tạo tài khoản");

        setSize(1200,700);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        BackgroundPanel background =
                new BackgroundPanel("images/bg.jpg");

        background.setLayout(new GridBagLayout());

        setContentPane(background);

        // ================= PANEL =================

        JPanel panel = new JPanel(){

            @Override
            protected void paintComponent(Graphics g){

                Graphics2D g2 =
                        (Graphics2D) g;

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                g2.setColor(
                        new Color(
                                0,
                                0,
                                0,
                                20
                        )
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
                new Dimension(
                        380,
                        400
                )
        );

        panel.setLayout(null);

        // ================= BACK ARROW =================

        JLabel backBtn = new JLabel("←");

        backBtn.setForeground(Color.WHITE);

        backBtn.setFont(new Font("Arial", Font.BOLD, 30));

        backBtn.setBounds(
                20,
                15,
                30,
                30
        );

        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // ================= TITLE =================

        JLabel title =
                new JLabel(
                        "Tạo tài khoản"
                );

        title.setForeground(Color.WHITE);

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        20
                )
        );

        title.setBounds(
                120,
                40,
                200,
                30
        );

        // ================= EMAIL =================

        JLabel lbEmail =
                new JLabel("Email");

        lbEmail.setForeground(
                Color.WHITE
        );

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

        // ================= PHONE =================

        JLabel lbPhone =
                new JLabel("Số điện thoại");

        lbPhone.setForeground(Color.WHITE);

        lbPhone.setBounds(
                50,
                160,
                300,
                20
        );

        phone.setBounds(
                50,
                185,
                280,
                35
        );

        // ================= USER =================

        JLabel lbUser =
                new JLabel(
                        "Tên tài khoản"
                );

        lbUser.setForeground(
                Color.WHITE
        );

        lbUser.setBounds(
                50,
                230,
                300,
                20
        );

        user.setBounds(
                50,
                255,
                280,
                35
        );

        // ================= BUTTON =================

        JButton create =
                new JButton(
                        "Tạo tài khoản"
                );

        create.setBounds(
                50,
                320,
                280,
                40
        );

        create.setFocusPainted(false);

        create.setBorderPainted(false);

        create.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR
                )
        );

        // ================= ADD =================

        panel.add(backBtn);
        panel.add(title);

        panel.add(lbEmail);
        panel.add(email);

        panel.add(lbPhone);
        panel.add(phone);

        panel.add(lbUser);
        panel.add(user);

        panel.add(create);

        add(panel);

        // ================= EVENT =================

        backBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new Login();
                dispose();
            }
        });

        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                backBtn.setForeground(Color.blue);
            }

            public void mouseExited(MouseEvent e) {
                backBtn.setForeground(Color.WHITE);
            }
        });

        create.addActionListener(
                e -> register()
        );

        setVisible(true);
    }

    // ==================================================
    // REGISTER
    // ==================================================

    void register(){

        String gmailText = email.getText().trim();

        String phoneText = phone.getText().trim();

        String usernameText = user.getText().trim();

        if(gmailText.isEmpty()){

            JOptionPane.showMessageDialog(
                    this,
                    "Nhập email"
            );

            return;
        }

        if(usernameText.isEmpty()){

            JOptionPane.showMessageDialog(
                    this,
                    "Nhập tên tài khoản"
            );

            return;
        }

        try{

            Connection c =
                    Database.connect();

            String checkSql =
                    "SELECT * FROM users WHERE username=? OR email=? OR phone=? ";

            PreparedStatement checkPs =
                    c.prepareStatement(checkSql);

            checkPs.setString(
                    1,
                    usernameText
            );

            checkPs.setString(
                    2,
                    gmailText
            );

            checkPs.setString(
                    3,
                    phoneText
            );

            ResultSet rs =
                    checkPs.executeQuery();

            if(rs.next()){

                JOptionPane.showMessageDialog(
                        this,
                        "Email hoặc tên tài khoản đã tồn tại"
                );

                return;
            }

            new PasswordRegisterForm(
                    gmailText,
                    phoneText,
                    usernameText
            );

            dispose();

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        new Register();
    }
}