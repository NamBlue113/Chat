package com.messenger.shared.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private long conversationId;
    private long senderId;
    private String content;
    private String messageType;
    private String status;
    private boolean unsent;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient convenience fields for backward compatibility
    private transient String senderDisplayName;
    private transient String filePath;
    private transient long fileSize;
    private transient String reaction;
    private transient int chunkIndex;
    private transient byte[] chunkData;
    private transient int totalChunks;

    public Message() {
        this.messageType = "TEXT";
        this.status = "SENT";
        this.unsent = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.filePath = "";
        this.chunkIndex = -1;
        this.totalChunks = 0;
    }

    public Message(long id, long senderId, long conversationId, String content, String messageType, String status) {
        this();
        this.id = id;
        this.senderId = senderId;
        this.conversationId = conversationId;
        this.content = content;
        this.messageType = messageType;
        this.status = status;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }

    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isUnsent() { return unsent; }
    public void setUnsent(boolean unsent) { this.unsent = unsent; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // Transient accessors
    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public byte[] getChunkData() { return chunkData; }
    public void setChunkData(byte[] chunkData) { this.chunkData = chunkData; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    @Override
    public String toString() {
        return "Message{id=" + id + ", senderId=" + senderId + ", conversationId=" + conversationId
                + ", type=" + messageType + ", status=" + status + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((Message) o).id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }
}
