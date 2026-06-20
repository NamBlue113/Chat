package Client.ui;

import javax.swing.*;
import java.awt.*;

class BubblePanel extends JPanel {

    private Color color;

    public BubblePanel(Color color){
        this.color = color;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g){

        Graphics2D g2 =
                (Graphics2D) g;

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g2.setColor(color);

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
}