package ChatServer;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {

    // username -> client
    private static final Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(5001)) {

            System.out.println("Chat Server started on port 5001...");

            while (true) {

                Socket socket = serverSocket.accept();

                System.out.println("New client connected");

                ClientHandler client = new ClientHandler(socket);

                new Thread(client).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // CLIENT HANDLER
    // ==========================================
    static class ClientHandler implements Runnable {

        private Socket socket;

        private DataInputStream in;

        private DataOutputStream out;

        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {

                in = new DataInputStream(
                        socket.getInputStream()
                );

                out = new DataOutputStream(
                        socket.getOutputStream()
                );

                // ===== LOGIN =====
                username = in.readUTF();

                clients.put(username, this);

                System.out.println(username + " logged in");

                // ===== RECEIVE LOOP =====
                while (true) {

                    String type = in.readUTF();

                    // ======================
                    // MESSAGE
                    // ======================
                    if (type.equals("MSG")) {

                        String receiver = in.readUTF();

                        String content = in.readUTF();

                        ClientHandler client =
                                clients.get(receiver);

                        if (client != null) {

                            synchronized (client.out) {

                                client.out.writeUTF("MSG");

                                client.out.writeUTF(username);

                                client.out.writeUTF(content);

                                client.out.flush();
                            }
                        }
                    }

                    // ======================
                    // FILE / IMAGE
                    // ======================
                    else if (type.equals("FILE")) {

                        String receiver =
                                in.readUTF();

                        String fileName =
                                in.readUTF();

                        boolean hasSecret =
                                in.readBoolean();

                        long size =
                                in.readLong();

                        byte[] data =
                                new byte[(int) size];

                        in.readFully(data);

                        ClientHandler client =
                                clients.get(receiver);

                        if (client != null) {

                            synchronized (client.out) {

                                client.out.writeUTF("FILE");

                                // người gửi
                                client.out.writeUTF(username);

                                client.out.writeUTF(fileName);

                                client.out.writeBoolean(
                                        hasSecret
                                );

                                client.out.writeLong(size);

                                client.out.write(data);

                                client.out.flush();
                            }
                        }
                    }

                    else if(type.equals("CALL")){

                        String receiver = in.readUTF();

                        ClientHandler client = clients.get(receiver);

                        if(client != null){

                            client.out.writeUTF("CALL");
                            client.out.writeUTF(username);
                            client.out.flush();
                        }
                    }

                    else if(type.equals("CALL_ACCEPT")){

                        String receiver = in.readUTF();

                        ClientHandler client = clients.get(receiver);

                        if(client != null){

                            client.out.writeUTF("CALL_ACCEPT");
                            client.out.writeUTF(username);
                            client.out.flush();
                        }
                    }

                    else if(type.equals("CALL_END")){

                        String receiver = in.readUTF();

                        ClientHandler client = clients.get(receiver);

                        if(client != null){

                            client.out.writeUTF("CALL_END");
                            client.out.writeUTF(username);
                            client.out.flush();
                        }
                    }

                    else if(type.equals("VIDEO")){

                        String receiver = in.readUTF();

                        long size = in.readLong();

                        byte[] data = new byte[(int)size];

                        in.readFully(data);

                        ClientHandler client = clients.get(receiver);

                        if(client != null){

                            synchronized (client.out){

                                client.out.writeUTF("VIDEO");

                                client.out.writeUTF(username);

                                client.out.writeLong(size);

                                client.out.write(data);

                                client.out.flush();
                            }
                        }
                    }

                    else if(type.equals("AUDIO_FRAME")){
//
//
//                        System.out.println(
//                                "Audio frame from "
//                                        + username
//                        );

                        String receiver =
                                in.readUTF();

                        int length =
                                in.readInt();

                        byte[] audio =
                                new byte[length];

                        in.readFully(audio);

                        ClientHandler target =
                                clients.get(receiver);

                        if(target != null){

                            synchronized (target.out){

                                target.out.writeUTF(
                                        "AUDIO_FRAME"
                                );

                                target.out.writeUTF(
                                        username
                                );

                                target.out.writeInt(
                                        length
                                );

                                target.out.write(
                                        audio,
                                        0,
                                        length
                                );

                                target.out.flush();
                            }
                        }
                    }
                }

            } catch (Exception e) {

                System.out.println(
                        username + " disconnected"
                );

            } finally {

                try {

                    if (username != null) {

                        clients.remove(username);

                        System.out.println(
                                username + " removed"
                        );
                    }

                    if (socket != null) {
                        socket.close();
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}