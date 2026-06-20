package Client.ui;

import Client.service.SocketService;
import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import javax.sound.sampled.*;

public class VideoCallFrame extends JFrame {

    private JLabel localLabel;
    private JLabel remoteLabel;

    private Webcam webcam;

    private SocketService socketService;
    private String friend;
    private static VideoCallFrame instance;
    private boolean running = true;
    private JButton endButton;
    private boolean ending = false;

    private TargetDataLine microphone;

    private SourceDataLine speakers;

    public VideoCallFrame(SocketService socketService,
                          String friend) {

        instance = this;

        this.socketService = socketService;
        this.friend = friend;

        setTitle("Video Call - " + friend);

        setSize(800, 500);

        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // KHỞI TẠO TRƯỚC
        localLabel = new JLabel();
        remoteLabel = new JLabel();

        endButton = new JButton("Kết thúc");

        endButton.addActionListener(
                e -> closeCall()
        );

        JPanel videoPanel =
                new JPanel(
                        new GridLayout(1, 2)
                );

        videoPanel.add(localLabel);
        videoPanel.add(remoteLabel);

        add(videoPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel();

        bottom.add(endButton);

        add(bottom, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setVisible(true);

        webcam = Webcam.getDefault();

        if (webcam == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Không tìm thấy webcam"
            );

            dispose();
            return;
        }

        try {

            if (!webcam.isOpen()) {

                webcam.open();
            }

        } catch(Exception e) {

            JOptionPane.showMessageDialog(
                    this,
                    "Webcam đang được sử dụng"
            );

            return;
        }
        initAudio();

        startAudioCapture();

        startCapture();
    }

    private void startCapture() {

        new Thread(() -> {

            while (running) {

                try {

                    BufferedImage original = webcam.getImage();

                    if(original == null){
                        continue;
                    }

                    BufferedImage resized =
                            new BufferedImage(
                                    320,
                                    240,
                                    BufferedImage.TYPE_INT_RGB
                            );

                    Graphics2D g = resized.createGraphics();

                    g.drawImage(
                            original,
                            0,
                            0,
                            320,
                            240,
                            null
                    );

                    g.dispose();

                    localLabel.setIcon(
                            new ImageIcon(resized)
                    );

                    ByteArrayOutputStream baos =
                            new ByteArrayOutputStream();

                    ImageIO.write(
                            resized,
                            "jpg",
                            baos
                    );

                    socketService.sendVideoFrame(
                            friend,
                            baos.toByteArray()
                    );

                    Thread.sleep(30);

                } catch (Exception e) {
                    break;
                }
            }

        }).start();
    }

    private void closeCall(){

        if(ending) return;

        ending = true;

        socketService.endCall(friend);

        dispose();
    }

    public static void updateRemoteFrame(
            byte[] imageData
    ) {

        VideoCallFrame frame = instance;

        if(frame == null) return;

        try {

            BufferedImage image =
                    ImageIO.read(
                            new ByteArrayInputStream(
                                    imageData
                            )
                    );

            SwingUtilities.invokeLater(() -> {

                if(frame.isDisplayable()){

                    frame.remoteLabel.setIcon(
                            new ImageIcon(image)
                    );
                }
            });

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    public static void closeRemoteCall(){

        if(instance != null){

            instance.ending = true;

            instance.dispose();
        }
    }

    private void initAudio() {

        try {

            AudioFormat format =
                    new AudioFormat(
                            44100,
                            16,
                            1,
                            true,
                            false
                    );

            DataLine.Info micInfo =
                    new DataLine.Info(
                            TargetDataLine.class,
                            format
                    );

            microphone =
                    (TargetDataLine)
                            AudioSystem.getLine(
                                    micInfo
                            );

            microphone.open(format);

            microphone.start();

            DataLine.Info speakerInfo =
                    new DataLine.Info(
                            SourceDataLine.class,
                            format
                    );

            speakers =
                    (SourceDataLine)
                            AudioSystem.getLine(
                                    speakerInfo
                            );

            speakers.open(format);

            speakers.start();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void startAudioCapture() {

        new Thread(() -> {

            byte[] buffer = new byte[1024];

            while(running){

                try {

                    int count =
                            microphone.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

//                    if(speakers != null){
//                        speakers.write(buffer, 0, count);
//                    }

                    socketService.sendAudioFrame(
                            friend,
                            buffer,
                            count
                    );

                } catch (Exception e) {

                    break;
                }

            }

        }).start();
    }

    public void playAudio(
            byte[] data
    ) {

        try {

            System.out.println(
                    "PLAY AUDIO: " + data.length
            );

            if(speakers != null){

                speakers.write(
                        data,
                        0,
                        data.length
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    public static void updateAudio(byte[] data) {

        VideoCallFrame frame = instance;

        if(frame != null){

            frame.playAudio(data);
        }
    }

    @Override
    public void dispose() {

        running = false;

        if(microphone != null){

            microphone.stop();

            microphone.close();
        }

        if(speakers != null){

            speakers.stop();

            speakers.close();
        }

        if(webcam != null &&
                webcam.isOpen()){

            webcam.close();
        }

        instance = null;

        super.dispose();
    }
}