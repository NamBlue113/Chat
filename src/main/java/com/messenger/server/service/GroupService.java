package com.messenger.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.messenger.server.db.DatabaseManager;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.Group;
import com.messenger.shared.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class GroupService {

    private static final Logger logger = LoggerFactory.getLogger(GroupService.class);

    private GroupService() {
        throw new UnsupportedOperationException("GroupService is a utility class");
    }

    public static class GroupResult {
        private final boolean success;
        private final String message;
        private final Group group;
        public GroupResult(boolean s, String m, Group g) { this.success = s; this.message = m; this.group = g; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Group getGroup() { return group; }
    }

    public static GroupResult createGroup(String name, long creatorId) {
        if (name == null || name.trim().isEmpty()) {
            return new GroupResult(false, "Group name cannot be empty", null);
        }
        // conversations table: type='GROUP', title=name, owner_id=creatorId
        String insertConv = "INSERT INTO conversations (type, title, owner_id) VALUES ('GROUP', ?, ?)";
        String insertMember = "INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, 'ADMIN')";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long convId;
                try (PreparedStatement ps = conn.prepareStatement(insertConv, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setLong(2, creatorId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) { conn.rollback(); return new GroupResult(false, "Failed to create group", null); }
                        convId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(insertMember)) {
                    ps.setLong(1, convId); ps.setLong(2, creatorId); ps.executeUpdate();
                }
                conn.commit();
                Group group = new Group(convId, name.trim(), creatorId);
                group.addMember(creatorId, Protocol.ROLE_ADMIN);
                logger.info("Group created: '{}' (id={}) by user {}", name, convId, creatorId);
                return new GroupResult(true, "Group created", group);
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        } catch (SQLException e) {
            logger.error("Failed to create group '{}': {}", name, e.getMessage());
            return new GroupResult(false, "Database error", null);
        }
    }

    public static boolean addMember(long conversationId, long userId) {
        String sql = "INSERT IGNORE INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, 'MEMBER')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { logger.error("Failed to add member: {}", e.getMessage()); return false; }
    }

    public static boolean removeMember(long conversationId, long userId) {
        String sql = "DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { logger.error("Failed to remove member: {}", e.getMessage()); return false; }
    }

    /**
     * Lấy thông tin nhóm đầy đủ (bao gồm cả danh sách thành viên).
     */
    public static JsonObject getGroupInfo(long conversationId) {
        JsonObject info = new JsonObject();
        String sql = "SELECT id, title, avatar_url, description, owner_id, created_at FROM conversations WHERE id = ? AND type = 'GROUP'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.addProperty("id", rs.getLong("id"));
                    info.addProperty("name", rs.getString("title"));
                    info.addProperty("avatarUrl", rs.getString("avatar_url") != null ? rs.getString("avatar_url") : "");
                    info.addProperty("description", rs.getString("description") != null ? rs.getString("description") : "");
                    info.addProperty("ownerId", rs.getLong("owner_id"));
                    info.addProperty("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").getTime() : 0);
                }
            }
            // Lấy danh sách thành viên
            JsonArray membersArr = new JsonArray();
            String mSql = "SELECT cm.user_id, cm.role, u.username, u.display_name, u.avatar_url "
                       + "FROM conversation_members cm INNER JOIN users u ON cm.user_id = u.id "
                       + "WHERE cm.conversation_id = ? ORDER BY cm.role, u.display_name";
            try (PreparedStatement pm = conn.prepareStatement(mSql)) {
                pm.setLong(1, conversationId);
                try (ResultSet rm = pm.executeQuery()) {
                    while (rm.next()) {
                        JsonObject m = new JsonObject();
                        m.addProperty("userId", rm.getLong("user_id"));
                        m.addProperty("role", rm.getString("role"));
                        m.addProperty("username", rm.getString("username"));
                        m.addProperty("displayName", rm.getString("display_name"));
                        m.addProperty("avatarUrl", rm.getString("avatar_url") != null ? rm.getString("avatar_url") : "");
                        membersArr.add(m);
                    }
                }
            }
            info.add("members", membersArr);
        } catch (SQLException e) {
            logger.error("Failed to get group info {}: {}", conversationId, e.getMessage());
        }
        return info;
    }

    public static boolean updateGroup(long conversationId, String name, String avatarUrl, String description) {
        StringBuilder sql = new StringBuilder("UPDATE conversations SET ");
        List<String> sets = new ArrayList<>();
        if (name != null) sets.add("title = ?");
        if (avatarUrl != null) sets.add("avatar_url = ?");
        if (description != null) sets.add("description = ?");
        if (sets.isEmpty()) return false;
        sql.append(String.join(", ", sets));
        sql.append(" WHERE id = ? AND type = 'GROUP'");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (name != null) ps.setString(idx++, name.trim());
            if (avatarUrl != null) ps.setString(idx++, avatarUrl);
            if (description != null) ps.setString(idx++, description.trim());
            ps.setLong(idx, conversationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update group {}: {}", conversationId, e.getMessage());
            return false;
        }
    }

    public static boolean promoteMember(long conversationId, long userId) {
        String sql = "UPDATE conversation_members SET role = 'ADMIN' WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to promote member {} in group {}: {}", userId, conversationId, e.getMessage());
            return false;
        }
    }

    public static boolean demoteMember(long conversationId, long userId) {
        String sql = "UPDATE conversation_members SET role = 'MEMBER' WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to demote member {} in group {}: {}", userId, conversationId, e.getMessage());
            return false;
        }
    }

    public static List<Group> getUserGroups(long userId) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT c.id, c.title, c.owner_id, c.avatar_url, c.created_at "
                   + "FROM conversations c INNER JOIN conversation_members cm ON c.id = cm.conversation_id "
                   + "WHERE cm.user_id = ? AND c.type = 'GROUP' ORDER BY c.title";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group();
                    g.setId(rs.getLong("id"));
                    g.setName(rs.getString("title"));
                    g.setCreatorId(rs.getLong("owner_id"));
                    g.setAvatarUrl(rs.getString("avatar_url"));
                    g.setCreatedAt(rs.getTimestamp("created_at"));
                    groups.add(g);
                }
            }
            for (Group g : groups) loadMembers(g);
        } catch (SQLException e) { logger.error("Failed to get groups: {}", e.getMessage()); }
        return groups;
    }

    public static List<Group.GroupMember> getGroupMembers(long conversationId) {
        List<Group.GroupMember> members = new ArrayList<>();
        String sql = "SELECT cm.user_id, cm.role, u.username, u.display_name "
                   + "FROM conversation_members cm INNER JOIN users u ON cm.user_id = u.id WHERE cm.conversation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group.GroupMember m = new Group.GroupMember();
                    m.setUserId(rs.getLong("user_id")); m.setRole(rs.getString("role"));
                    m.setUsername(rs.getString("username")); m.setDisplayName(rs.getString("display_name"));
                    members.add(m);
                }
            }
        } catch (SQLException e) { logger.error("Failed to get members: {}", e.getMessage()); }
        return members;
    }

    private static void loadMembers(Group group) {
        String sql = "SELECT cm.user_id, cm.role, u.username, u.display_name "
                   + "FROM conversation_members cm INNER JOIN users u ON cm.user_id = u.id WHERE cm.conversation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, group.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    group.addMember(rs.getLong("user_id"), rs.getString("role"));
                }
            }
        } catch (SQLException e) { logger.error("Failed to load members: {}", e.getMessage()); }
    }

    public static long saveGroupMessage(long senderId, long conversationId, String content, String messageType) {
        String sql = "INSERT INTO messages (sender_id, conversation_id, content, message_type, status) VALUES (?, ?, ?, ?, 'SENT')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, senderId); ps.setLong(2, conversationId);
            ps.setString(3, content); ps.setString(4, messageType != null ? messageType : "TEXT");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        } catch (SQLException e) { logger.error("Failed to save group message: {}", e.getMessage()); }
        return -1;
    }

    public static List<Message> getGroupMessages(long conversationId, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT m.id, m.sender_id, m.conversation_id, m.content, m.message_type, m.status, "
                   + "m.is_unsent, m.created_at, u.display_name "
                   + "FROM messages m INNER JOIN users u ON m.sender_id = u.id "
                   + "WHERE m.conversation_id = ? AND m.is_unsent = 0 ORDER BY m.created_at DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setId(rs.getLong("id"));
                    msg.setSenderId(rs.getLong("sender_id"));
                    msg.setConversationId(rs.getLong("conversation_id"));
                    msg.setContent(rs.getString("content"));
                    msg.setMessageType(rs.getString("message_type"));
                    msg.setStatus(rs.getString("status"));
                    msg.setUnsent(rs.getBoolean("is_unsent"));
                    msg.setCreatedAt(rs.getTimestamp("created_at"));
                    msg.setSenderDisplayName(rs.getString("display_name"));
                    messages.add(msg);
                }
            }
        } catch (SQLException e) { logger.error("Failed to get messages: {}", e.getMessage()); }
        return messages;
    }
}
