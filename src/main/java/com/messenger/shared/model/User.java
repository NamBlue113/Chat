package com.messenger.shared.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String presence;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastSeen;

    public User() {
        this.presence = "OFFLINE";
        this.displayName = "";
        this.avatarUrl = "";
        this.email = "";
    }

    public User(long id, String username, String displayName, String avatarUrl, String presence) {
        this();
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.presence = presence;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getPresence() { return presence; }
    public void setPresence(String presence) { this.presence = presence; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }

    public boolean isOnline() { return "ONLINE".equals(presence); }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', displayName='" + displayName + "', presence=" + presence + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((User) o).id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }
}
