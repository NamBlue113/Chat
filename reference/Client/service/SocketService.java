package Client.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.nio.file.Files;

import static login_register.Session.username;

public class SocketService {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String currentUser;

    private SocketListener listener;

    public SocketService(String currentUser) {

        this.currentUser = currentUser;
    }

    public void setListener(SocketListener listener) {

        this.listener = listener;
    }

    public void connect() {

        try {

            socket =
                    new Socket("172.26.51.126",5001);

            in =
                    new DataInputStream(
                            socket.getInputStream()
                    );

            out =
                    new DataOutputStream(
                            socket.getOutputStream()
                    );

            out.writeUTF(currentUser);
            out.flush();

            new Thread(() -> {

                try {

                    while(true){

                        String type =
                                in.readUTF();

                        if(type.equals("MSG")){

                            String sender =
                                    in.readUTF();

                            String content =
                                    in.readUTF();

                            if(listener != null){

                                listener.onMessageReceived(
                                        sender,
                                        content
                                );
                            }
                        }

                        else if(type.equals("FILE")){

                            receiveFile();
                        }

                        else if(type.equals("CALL")){

                            String sender = in.readUTF();

                            listener.onCallReceived(sender);
                        }

                        else if(type.equals("CALL_ACCEPT")){

                            String sender = in.readUTF();

                            listener.onCallAccepted(sender);
                        }

                        else if(type.equals("CALL_END")){

                            String sender = in.readUTF();

                            listener.onCallEnded(sender);
                        }

                        else if(type.equals("VIDEO")){

                            String sender = in.readUTF();

                            long size = in.readLong();

                            byte[] data = new byte[(int)size];

                            in.readFully(data);

                            listener.onVideoFrame(sender,data);
                        }

                        else if(type.equals("AUDIO_FRAME")){

                            String sender = in.readUTF();

                            int length = in.readInt();

                            byte[] audioData = new byte[length];

                            in.readFully(audioData);

                            if(listener != null){
                                listener.onAudioFrame(
                                        sender,
                                        audioData
                                );
                            }
//                            System.out.println(
//                                    username +
//                                            " -> AUDIO " +
//                                            length
//                            );
                        }
                    }

                }catch(Exception e){

                    e.printStackTrace();
                }

            }).start();

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public void sendMessage(
            String receiver,
            String content){

        try {

            out.writeUTF("MSG");

            out.writeUTF(receiver);

            out.writeUTF(content);

            out.flush();

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public void sendFile(String receiver, File file){

        try{

            byte[] data =
                    Files.readAllBytes(file.toPath());

            out.writeUTF("FILE");
            out.writeUTF(receiver);
            out.writeUTF(file.getName());
            out.writeBoolean(false);
            out.writeLong(data.length);
            out.write(data);
            out.flush();

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void receiveFile(){

        try {

            String sender =
                    in.readUTF();

            String fileName =
                    in.readUTF();

            in.readBoolean();

            long size =
                    in.readLong();

            byte[] data =
                    new byte[(int)size];

            in.readFully(data);

            if(listener != null){

                listener.onFileReceived(
                        sender,
                        fileName,
                        data
                );
            }

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public void sendCall(String receiver){
        try{
            synchronized (out){
                out.writeUTF("CALL");
                out.writeUTF(receiver);
                out.flush();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void acceptCall(String receiver){

        try{

            out.writeUTF("CALL_ACCEPT");
            out.writeUTF(receiver);
            out.flush();

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public void endCall(String receiver){

        try{

            out.writeUTF("CALL_END");
            out.writeUTF(receiver);
            out.flush();

        }catch(Exception e){

            e.printStackTrace();
        }
    }

    public void sendVideoFrame(
            String receiver,
            byte[] imageData){

        try{

            synchronized (out){

                out.writeUTF("VIDEO");

                out.writeUTF(receiver);

                out.writeLong(imageData.length);

                out.write(imageData);

                out.flush();
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void sendAudioFrame(
            String receiver,
            byte[] data,
            int length
    ) {

        try {

            synchronized (out) {

                out.writeUTF("AUDIO_FRAME");

                out.writeUTF(receiver);

                out.writeInt(length);

                out.write(data, 0, length);

                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DataOutputStream getOut() {

        return out;
    }
}