package com.messenger.server.service;

import com.messenger.server.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages conversations (chat rooms) for 1-1 and group chats.
 */
public final class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    private ConversationService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Find or create a private (1-1) conversation between two users.
     */
    public static long getOrCreatePrivateConversation(long user1Id, long user2Id) {
        long existing = findPrivateConversation(user1Id, user2Id);
        if (existing > 0) return existing;

        // Create new conversation
        String insertConv = "INSERT INTO conversations (type) VALUES ('PRIVATE')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertConv, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            long convId;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) return -1;
                convId = rs.getLong(1);
            }

            // Add both members
            String insertMember = "INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, 'MEMBER')";
            try (PreparedStatement pm = conn.prepareStatement(insertMember)) {
                pm.setLong(1, convId);
                pm.setLong(2, user1Id);
                pm.executeUpdate();
                pm.setLong(2, user2Id);
                pm.executeUpdate();
            }
            logger.info("Created private conversation {} for users {} and {}", convId, user1Id, user2Id);
            return convId;
        } catch (SQLException e) {
            logger.error("Failed to create private conversation: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Find existing private conversation between two users. Returns -1 if none.
     */
    public static long findPrivateConversation(long user1Id, long user2Id) {
        String sql = "SELECT cm1.conversation_id FROM conversation_members cm1 " +
                     "JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id " +
                     "JOIN conversations c ON c.id = cm1.conversation_id " +
                     "WHERE cm1.user_id = ? AND cm2.user_id = ? AND c.type = 'PRIVATE' LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, user1Id);
            ps.setLong(2, user2Id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to find private conversation: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Get the conversation ID for a group. Returns -1 if not found.
     */
    public static long getGroupConversationId(long groupId) {
        String sql = "SELECT id FROM conversations WHERE type = 'GROUP' AND owner_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get group conversation: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Create a group conversation.
     */
    public static long createGroupConversation(String groupName, long creatorId) {
        String sql = "INSERT INTO conversations (type, title, owner_id) VALUES ('GROUP', ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, groupName);
            ps.setLong(2, creatorId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long convId = rs.getLong(1);
                    addMember(convId, creatorId);
                    logger.info("Created group conversation {} for group '{}'", convId, groupName);
                    return convId;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create group conversation: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Add a user to a conversation.
     */
    public static boolean addMember(long conversationId, long userId) {
        String sql = "INSERT IGNORE INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to add member {} to conv {}: {}", userId, conversationId, e.getMessage());
            return false;
        }
    }

    /**
     * Remove a user from a conversation (hide/delete from their list).
     * The conversation and its messages are preserved for other members.
     */
    public static boolean hideConversation(long conversationId, long userId) {
        String sql = "DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
            int rows = ps.executeUpdate();
            logger.info("User {} removed from conversation {}", userId, conversationId);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("Failed to hide conversation {} for user {}: {}", conversationId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all member IDs of a conversation.
     */
    public static List<Long> getMembers(long conversationId) {
        List<Long> members = new ArrayList<>();
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) members.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            logger.error("Failed to get members: {}", e.getMessage());
        }
        return members;
    }
}
