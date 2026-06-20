package login_register;

import javax.swing.*;
import java.awt.*;

class BackgroundPanel extends JPanel {

    private Image image;

    public BackgroundPanel(String path) {

        image =
                new ImageIcon(path)
                        .getImage();
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        g.drawImage(
                image,
                0,
                0,
                getWidth(),
                getHeight(),
                this
        );
    }
}