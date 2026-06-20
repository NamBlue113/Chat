package Client.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatPanel extends JPanel {

    private JPanel messagePanel;
    private JScrollPane chatScroll;

    public ChatPanel() {

        setLayout(new BorderLayout());

        messagePanel = new JPanel();
        messagePanel.setLayout(new GridBagLayout());
        messagePanel.setBackground(Color.WHITE);

        chatScroll = new JScrollPane(
                messagePanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        add(chatScroll, BorderLayout.CENTER);
    }

    public void clearMessages() {

        messagePanel.removeAll();
        messagePanel.revalidate();
        messagePanel.repaint();
    }

    public void addMessageBubble(
            String text,
            boolean isMine,
            String time) {

        JPanel wrapper =
                new JPanel(new BorderLayout());

        wrapper.setOpaque(false);

        JPanel bubble =
                new BubblePanel(
                        isMine
                                ? new Color(0,120,255)
                                : new Color(235,235,235)
                );

        bubble.setBorder(
                BorderFactory.createEmptyBorder(
                        10,15,10,15
                )
        );

        JPanel content =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                3,
                                0
                        )
                );

        content.setOpaque(false);

        Pattern pattern =
                Pattern.compile("\\[ICON:(.*?)\\]");

        Matcher matcher =
                pattern.matcher(text);

        int last = 0;

        while(matcher.find()){

            String before =
                    text.substring(last, matcher.start());

            if(!before.isEmpty()){

                JLabel lbl =
                        new JLabel(before);

                if(isMine){
                    lbl.setForeground(Color.WHITE);
                }

                content.add(lbl);
            }

            String iconName =
                    matcher.group(1);

            java.net.URL url =
                    getClass().getResource(
                            "/icons/" + iconName
                    );

            if(url != null){

                ImageIcon icon =
                        new ImageIcon(url);

                Image img =
                        icon.getImage().getScaledInstance(
                                28,
                                28,
                                Image.SCALE_SMOOTH
                        );

                content.add(
                        new JLabel(
                                new ImageIcon(img)
                        )
                );
            }

            last = matcher.end();
        }

        String remain =
                text.substring(last);

        if(!remain.isEmpty()){

            JLabel lbl =
                    new JLabel(remain);

            if(isMine){
                lbl.setForeground(Color.WHITE);
            }

            content.add(lbl);
        }

        bubble.add(content);

        JPanel itemPanel =
                new JPanel();

        itemPanel.setOpaque(false);

        itemPanel.setLayout(
                new BoxLayout(
                        itemPanel,
                        BoxLayout.Y_AXIS
                )
        );

        itemPanel.add(bubble);

        JLabel timeLabel =
                new JLabel(time);

        timeLabel.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        11
                )
        );

        timeLabel.setForeground(
                Color.GRAY
        );

        timeLabel.setAlignmentX(
                isMine
                        ? Component.RIGHT_ALIGNMENT
                        : Component.LEFT_ALIGNMENT
        );

        itemPanel.add(timeLabel);

        wrapper.add(
                itemPanel,
                isMine
                        ? BorderLayout.EAST
                        : BorderLayout.WEST
        );

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = messagePanel.getComponentCount();
        gbc.weightx = 1;

        gbc.anchor =
                isMine
                        ? GridBagConstraints.EAST
                        : GridBagConstraints.WEST;

        messagePanel.add(wrapper, gbc);

        refresh();
    }

    public void addFileBubble(
            boolean isMine,
            String fileName,
            String base64,
            String time){

        JButton btn;

        boolean isImage =
                fileName.toLowerCase()
                        .matches(".*\\.(png|jpg|jpeg|gif)");

        if(isImage){

            byte[] data =
                    Base64.getDecoder()
                            .decode(base64);

            ImageIcon icon =
                    new ImageIcon(data);

            Image img =
                    icon.getImage()
                            .getScaledInstance(
                                    150,
                                    -1,
                                    Image.SCALE_SMOOTH
                            );

            btn =
                    new JButton(
                            new ImageIcon(img)
                    );

        }else{

            btn =
                    new JButton(
                            "📄 " + fileName
                    );
        }

        btn.addActionListener(e -> {

            JFileChooser chooser =
                    new JFileChooser();

            chooser.setSelectedFile(
                    new File(fileName)
            );

            if(chooser.showSaveDialog(this)
                    == JFileChooser.APPROVE_OPTION){

                try{

                    byte[] data =
                            Base64.getDecoder()
                                    .decode(base64);

                    java.nio.file.Files.write(
                            chooser.getSelectedFile()
                                    .toPath(),
                            data
                    );

                }catch(Exception ex){

                    ex.printStackTrace();
                }
            }
        });

        JPanel wrapper =
                new JPanel(new BorderLayout());

        wrapper.setOpaque(false);

        JPanel itemPanel =
                new JPanel();

        itemPanel.setOpaque(false);

        itemPanel.setLayout(
                new BoxLayout(
                        itemPanel,
                        BoxLayout.Y_AXIS
                )
        );

        itemPanel.add(btn);

        JLabel timeLabel =
                new JLabel(time);

        timeLabel.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        11
                )
        );

        timeLabel.setForeground(Color.GRAY);

        timeLabel.setAlignmentX(
                isMine
                        ? Component.RIGHT_ALIGNMENT
                        : Component.LEFT_ALIGNMENT
        );

        itemPanel.add(timeLabel);

        wrapper.add(
                itemPanel,
                isMine
                        ? BorderLayout.EAST
                        : BorderLayout.WEST
        );

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = messagePanel.getComponentCount();
        gbc.weightx = 1;

        gbc.anchor =
                isMine
                        ? GridBagConstraints.EAST
                        : GridBagConstraints.WEST;

        messagePanel.add(wrapper, gbc);

        refresh();
    }

    private void refresh() {

        messagePanel.revalidate();
        messagePanel.repaint();

        SwingUtilities.invokeLater(() -> {

            JScrollBar bar =
                    chatScroll.getVerticalScrollBar();

            bar.setValue(bar.getMaximum());

            Timer timer = new Timer(100, e -> {
                bar.setValue(bar.getMaximum());
            });

            timer.setRepeats(false);
            timer.start();
        });
    }
}