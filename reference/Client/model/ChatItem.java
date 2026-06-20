package Client.model;

public class ChatItem {

    private String type;
    private String sender;
    private String content;
    private String fileName;
    private byte[] fileData;
    private String sentTime;

    public ChatItem(String type,
                    String sender,
                    String content,
                    String fileName,
                    byte[] fileData,
                    String sentTime) {

        this.type = type;
        this.sender = sender;
        this.content = content;
        this.fileName = fileName;
        this.fileData = fileData;
        this.sentTime = sentTime;
    }

    public String getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public String getSentTime() {
        return sentTime;
    }
}