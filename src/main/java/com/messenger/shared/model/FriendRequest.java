package com.messenger.shared.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class FriendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private long senderId;
    private long receiverId;
    private String status;
    private Timestamp createdAt;

    private transient String senderUsername;
    private transient String senderDisplayName;
    private transient String receiverUsername;
    private transient String receiverDisplayName;

    public FriendRequest() {
        this.status = "PENDING";
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public FriendRequest(long id, long senderId, long receiverId, String status) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = status;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }

    public long getReceiverId() { return receiverId; }
    public void setReceiverId(long receiverId) { this.receiverId = receiverId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }

    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }

    public String getReceiverDisplayName() { return receiverDisplayName; }
    public void setReceiverDisplayName(String receiverDisplayName) { this.receiverDisplayName = receiverDisplayName; }

    public boolean isPending() { return "PENDING".equals(status); }
    public boolean isAccepted() { return "ACCEPTED".equals(status); }
    public boolean isRejected() { return "REJECTED".equals(status); }

    @Override
    public String toString() {
        return "FriendRequest{id=" + id + ", sender=" + senderId + ", receiver=" + receiverId + ", status=" + status + "}";
    }
}
