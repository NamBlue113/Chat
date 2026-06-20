package Client.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class FriendPanel extends JPanel {

    private JPanel friendListPanel;

    public FriendPanel() {

        setLayout(new BorderLayout());

        friendListPanel =
                new JPanel();

        friendListPanel.setLayout(
                new BoxLayout(
                        friendListPanel,
                        BoxLayout.Y_AXIS
                )
        );

        add(
                new JScrollPane(friendListPanel),
                BorderLayout.CENTER
        );
    }

    public void loadFriends(
            List<String> friends,
            String keyword,
            Consumer<String> onClick){

        friendListPanel.removeAll();

        for(String name : friends){

            if(name.toLowerCase()
                    .contains(
                            keyword.toLowerCase()
                    )){

                friendListPanel.add(
                        createFriendItem(
                                name,
                                onClick
                        )
                );
            }
        }

        friendListPanel.revalidate();
        friendListPanel.repaint();
    }

    private JPanel createFriendItem(
            String name,
            Consumer<String> onClick){

        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        panel.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        70
                )
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        10,10,10,10
                )
        );

        JLabel avatar =
                new JLabel(
                        name.substring(0,1)
                                .toUpperCase()
                );

        avatar.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        avatar.setOpaque(true);

        avatar.setBackground(
                Color.LIGHT_GRAY
        );

        avatar.setPreferredSize(
                new Dimension(40,40)
        );

        JLabel lbl =
                new JLabel(name);

        lbl.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        15
                )
        );

        panel.add(
                avatar,
                BorderLayout.WEST
        );

        panel.add(
                lbl,
                BorderLayout.CENTER
        );

        panel.addMouseListener(
                new java.awt.event.MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            java.awt.event.MouseEvent e) {

                        onClick.accept(name);
                    }
                }
        );

        return panel;
    }
}