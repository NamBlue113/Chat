package Client.ui;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.function.Consumer;

public class EmojiDialog extends JDialog {

    public EmojiDialog(
            JFrame parent,
            Consumer<String> callback){

        super(parent,"Emoji",true);

        setSize(500,400);

        setLocationRelativeTo(parent);

        JPanel panel =
                new JPanel(
                        new GridLayout(
                                0,
                                6,
                                5,
                                5
                        )
                );

        String[] emojis = {
                "Thumbs Up Hand Sign Emoji.png",
                "heart.png",
                "Slightly Smiling Face Emoji.png",
                "Upside-Down Face Emoji.png",
                "Smiling Face with Tightly Closed eyes.png",
                "Tears of Joy Emoji.png",
                "Smirk Face Emoji.png",
                "Sad Face Emoji.png",
                "Nerd Emoji With Glasses.png",
                "Smiling Face Emoji with Blushed Cheeks.png",
                "Sunglasses Emoji.png",
                "Hungry Face Emoji.png",
                "Face without Mouth Emoji.png",
                "Thinking Emoji.png",
                "Heart Eyes Emoji.png",
                "Hushed Face Emoji.png",
                "Eye Roll Emoji.png",
                "Cold Sweat Emoji.png",
                "OMG Face Emoji.png",
                "Flushed Face Emoji.png",
                "Smiling Face with Halo.png",
                "Sleeping Emoji.png",
                "Very Mad Emoji.png",
                "Very Angry Emoji.png",
                "Loudly Crying Face Emoji.png",
                "Disappointed but Relieved Face Emoji.png",
                "Smiling Devil Emoji.png",
                "Alien Emoji.png"
        };

        for(String emoji : emojis){

            URL url =
                    getClass().getResource(
                            "/icons/" + emoji
                    );

            if(url == null){
                continue;
            }

            ImageIcon icon =
                    new ImageIcon(url);

            Image img =
                    icon.getImage()
                            .getScaledInstance(
                                    40,
                                    40,
                                    Image.SCALE_SMOOTH
                            );

            JButton btn =
                    new JButton(
                            new ImageIcon(img)
                    );

            btn.setBorderPainted(false);

            btn.setContentAreaFilled(false);

            btn.addActionListener(e -> {

                callback.accept(
                        "[ICON:" + emoji + "]"
                );

                dispose();
            });

            panel.add(btn);
        }

        add(new JScrollPane(panel));
    }
}