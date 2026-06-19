package com.messenger.shared.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private long creatorId;
    private String type;         // 'PRIVATE' or 'GROUP' in conversations
    private String avatarUrl;
    private Timestamp createdAt;
    private List<Long> memberIds;
    private List<GroupMember> members;

    public Group() {
        this.memberIds = new ArrayList<>();
        this.members = new ArrayList<>();
        this.avatarUrl = "";
        this.type = "GROUP";
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Group(long id, String name, long creatorId) {
        this();
        this.id = id;
        this.name = name;
        this.creatorId = creatorId;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatorId() { return creatorId; }
    public void setCreatorId(long creatorId) { this.creatorId = creatorId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) { this.memberIds = memberIds; }

    public List<GroupMember> getMembers() { return members; }
    public void setMembers(List<GroupMember> members) { this.members = members; }

    public void addMember(long userId, String role) {
        if (!memberIds.contains(userId)) {
            memberIds.add(userId);
            members.add(new GroupMember(userId, role));
        }
    }

    public void removeMember(long userId) {
        memberIds.remove(Long.valueOf(userId));
        members.removeIf(m -> m.getUserId() == userId);
    }

    public boolean hasMember(long userId) { return memberIds.contains(userId); }

    @Override
    public String toString() {
        return "Group{id=" + id + ", name='" + name + "', members=" + memberIds.size() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((Group) o).id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    public static class GroupMember implements Serializable {
        private static final long serialVersionUID = 1L;

        private long userId;
        private String role;
        private String username;
        private String displayName;

        public GroupMember() { this.role = "MEMBER"; }

        public GroupMember(long userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public boolean isAdmin() { return "ADMIN".equals(role); }

        @Override
        public String toString() {
            return "GroupMember{userId=" + userId + ", role=" + role + "}";
        }
    }
}
