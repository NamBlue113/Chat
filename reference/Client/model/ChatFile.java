package Client.model;

public class ChatFile {

    private String sender;
    private String receiver;
    private String fileName;
    private byte[] data;

    public ChatFile(String sender,
                    String receiver,
                    String fileName,
                    byte[] data) {

        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.data = data;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }
}