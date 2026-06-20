package login_register;

import database.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;

public class ForgotPasswordForm extends JFrame {

    JTextField email =
            new JTextField();

    JTextField otpField =
            new JTextField();

    JPasswordField newPass =
            new JPasswordField();

    String currentOTP = "";

    public ForgotPasswordForm(){

        setTitle(
                "Quên mật khẩu"
        );

        setSize(1200, 700);

        setLocationRelativeTo(null);

        setLayout(null);

        BackgroundPanel background =
                new BackgroundPanel("images/bg.jpg");

        background.setLayout(new GridBagLayout());

        setContentPane(background);

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
                        "Quên mật khẩu "
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
                250,
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

        // ================= BT OTP =================

        JButton sendOTP =
                new JButton(
                        "Gửi OTP"
                );

        sendOTP.setBounds(
                200,
                185,
                130,
                35
        );

        sendOTP.setFocusPainted(false);

        sendOTP.setBorderPainted(false);

        sendOTP.setCursor(
                new Cursor(Cursor.HAND_CURSOR)
        );

        // ================= OTP =================

        JLabel lbOTP =
                new JLabel("OTP");

        lbOTP.setBounds(
                50,
                160,
                100,
                20
        );

        lbOTP.setForeground(
                Color.WHITE
        );

        otpField.setBounds(
                50,
                185,
                130,
                35
        );

        // ================= MẬT KHẨU MỚI =================

        JLabel lbPass =
                new JLabel(
                        "Mật khẩu mới"
                );

        lbPass.setForeground(
                Color.WHITE
        );

        lbPass.setBounds(
                50,
                230,
                120,
                25
        );

        newPass.setBounds(
                50,
                255,
                280,
                35
        );

        // ================= ĐỔI MẬT KHẨU =================

        JButton reset =
                new JButton(
                        "Đổi mật khẩu"
                );

        reset.setBounds(
                50,
                320,
                280,
                40
        );

        reset.setFocusPainted(false);

        reset.setBorderPainted(false);

        reset.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR
                )
        );

        // ================= ADD =================

        panel.add(backBtn);
        panel.add(title);

        panel.add(lbEmail);
        panel.add(email);

        panel.add(lbOTP);
        panel.add(otpField);
        panel.add(sendOTP);

        panel.add(lbPass);
        panel.add(newPass);

        panel.add(reset);
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

        sendOTP.addActionListener(
                e -> sendOTP()
        );

        reset.addActionListener(
                e -> resetPassword()
        );

        setVisible(true);
    }

    void sendOTP() {

        try {

            Connection c = Database.connect();

            String sql =
                    "SELECT * FROM users WHERE email=?";

            PreparedStatement ps =
                    c.prepareStatement(sql);

            ps.setString(
                    1,
                    email.getText().trim()
            );

            ResultSet rs =
                    ps.executeQuery();

            if(!rs.next()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Email không tồn tại"
                );

                return;
            }

            currentOTP =
                    OTPGenerator.generateOTP();

            EmailSender.sendOTP(
                    email.getText().trim(),
                    currentOTP
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Đã gửi OTP"
            );

        } catch(Exception e) {

            e.printStackTrace();
        }
    }

    void resetPassword(){

        if(!otpField.getText()
                .equals(currentOTP)){

            JOptionPane.showMessageDialog(
                    this,
                    "OTP không đúng"
            );

            return;
        }

        try{

            Connection c =
                    Database.connect();

            String sql =
                    "UPDATE users SET password=? WHERE email=?";

            PreparedStatement ps =
                    c.prepareStatement(sql);

            ps.setString(
                    1,
                    new String(
                            newPass.getPassword()
                    )
            );

            ps.setString(
                    2,
                    email.getText()
            );

            ps.executeUpdate();

            JOptionPane.showMessageDialog(
                    this,
                    "Đổi mật khẩu thành công"
            );

            dispose();

            new Login();

        }catch(Exception e){

            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        new ForgotPasswordForm();
    }
}