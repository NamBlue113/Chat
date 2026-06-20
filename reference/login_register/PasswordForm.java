package login_register;

import Client.MainFrame;
import database.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PasswordForm extends JFrame {

    String username;

    JPasswordField pass =
            new JPasswordField();

    public PasswordForm(String user){

        this.username = user;

        setTitle("Nhập mật khẩu");

        setSize(1200,700);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

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
                15,
                15,
                30,
                30
        );

        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // ================= TITLE =================

        JLabel title =
                new JLabel(
                        "Xin chào " + user
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

        // ================= MẬT KHẨU =================

        JLabel lbPass =
                new JLabel("Mật khẩu");

        lbPass.setForeground(Color.WHITE);

        lbPass.setBounds(
                50,
                90,
                300,
                20
        );

        pass.setBounds(
                50,
                115,
                280,
                35
        );

        // ================= ĐĂNG NHẬP =================

        JButton loginBtn =
                new JButton("Đăng nhập");

        loginBtn.setBounds(
                50,
                320,
                280,
                40
        );

        loginBtn.setFocusPainted(false);

        loginBtn.setBorderPainted(false);

        loginBtn.setCursor(
                new Cursor(Cursor.HAND_CURSOR)
        );

        // ================= QUÊN MẬT KHẨU =================

        JLabel forgotPass = new JLabel("Quên mật khẩu?");

        forgotPass.setForeground(
                new Color(0, 120, 255));

        forgotPass.setCursor(
                new Cursor(Cursor.HAND_CURSOR));

        forgotPass.setBounds(
                50,
                160,
                120,
                25
        );


        forgotPass.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        new ForgotPasswordForm();
                        dispose();
                    }
                }
        );

        // ================= ADD =================

        panel.add(backBtn);

        panel.add(title);

        panel.add(lbPass);
        panel.add(pass);

        panel.add(loginBtn);
        panel.add(forgotPass);

        add(panel);

        loginBtn.addActionListener(
                e -> login()
        );

        setVisible(true);

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

    }

    // ================= LOGIN EVENT =================



    void login(){

        try{

            Connection c =
                    Database.connect();

            String sql =
                    "SELECT * FROM users WHERE username=? AND password=?";

            PreparedStatement ps =
                    c.prepareStatement(sql);

            ps.setString(
                    1,
                    username
            );

            ps.setString(
                    2,
                    new String(
                            pass.getPassword()
                    )
            );

            ResultSet rs =
                    ps.executeQuery();

            if(rs.next()){

                Session.userId =
                        rs.getInt("id");

                Session.username =
                        rs.getString("username");

                new MainFrame();

                dispose();

            }else{

                JOptionPane.showMessageDialog(
                        this,
                        "Sai mật khẩu"
                );
            }

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        new MainFrame();
    }
}