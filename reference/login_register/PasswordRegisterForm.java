package login_register;

import database.Database;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class PasswordRegisterForm extends JFrame {

    String email;
    String phone;
    String username;

    JPasswordField pass =
            new JPasswordField();

    JPasswordField confirmPass =
            new JPasswordField();

    public PasswordRegisterForm(
            String email,
            String phone,
            String username
    ){

        this.email = email;
        this.phone = phone;
        this.username = username;

        setTitle("Tạo mật khẩu");

        setSize(1200,700);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        BackgroundPanel bg =
                new BackgroundPanel("images/bg.jpg");

        bg.setLayout(new GridBagLayout());

        setContentPane(bg);

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
                new JLabel("Tạo mật khẩu");

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

        // ================= MẬT KHẨU =================

        JLabel lbPass =
                new JLabel("Mật khẩu");

        lbPass.setForeground(
                Color.WHITE
        );

        lbPass.setBounds(
                50,
                90,
                200,
                20
        );

        pass.setBounds(
                50,
                115,
                280,
                35
        );
        // ================= XÁC NHẬN MẬT KHẨU =================

        JLabel lbConfirm =
                new JLabel("Nhập lại mật khẩu");

        lbConfirm.setForeground(
                Color.WHITE
        );

        lbConfirm.setBounds(
                50,
                160,
                200,
                20
        );

        confirmPass.setBounds(
                50,
                185,
                280,
                35
        );

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
                new Cursor(Cursor.HAND_CURSOR)
        );

        create.addActionListener(
                e -> register()
        );

        panel.add(backBtn);
        panel.add(title);

        panel.add(lbPass);
        panel.add(pass);

        panel.add(lbConfirm);
        panel.add(confirmPass);

        panel.add(create);

        add(panel);

        setVisible(true);

        // ================= EVENT =================

        backBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new Register();
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

    void register(){

        String password =
                new String(
                        pass.getPassword()
                );

        String confirm =
                new String(
                        confirmPass.getPassword()
                );

        if(password.isEmpty()){

            JOptionPane.showMessageDialog(
                    this,
                    "Nhập mật khẩu"
            );

            return;
        }

        if(!password.equals(confirm)){

            JOptionPane.showMessageDialog(
                    this,
                    "Mật khẩu không khớp"
            );

            return;
        }

        try{

            Connection c =
                    Database.connect();

            String sql =
                    "INSERT INTO users(email,phone,username,password) VALUES(?,?,?,?)";

            PreparedStatement ps =
                    c.prepareStatement(sql);

            ps.setString(
                    1,
                    email
            );

            ps.setString(
                    2,
                    phone
            );

            ps.setString(
                    3,
                    username
            );

            ps.setString(
                    4,
                    password
            );

            ps.executeUpdate();

            JOptionPane.showMessageDialog(
                    this,
                    "Tạo tài khoản thành công"
            );

            dispose();

            new Login();

        }catch(Exception e){

            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        new PasswordRegisterForm("test","test","test");
    }
}