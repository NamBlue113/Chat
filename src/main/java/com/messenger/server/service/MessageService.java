package com.messenger.server.service;

import com.messenger.server.db.DatabaseManager;
import com.messenger.shared.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages message persistence in the MySQL database.
 */
public final class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    private MessageService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Save a message to the database. Returns the generated message ID, or -1 on failure.
     */
    public static long saveMessage(Message msg) {
        String sql = "INSERT INTO messages (conversation_id, sender_id, content, message_type, status) " +
                     "VALUES (?, ?, ?, ?, 'SENT')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, msg.getConversationId());
            ps.setLong(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setString(4, msg.getMessageType() != null ? msg.getMessageType() : "TEXT");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    msg.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save message: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Load message history for a conversation with pagination.
     * @param conversationId the conversation to load
     * @param limit max number of messages (default 50)
     * @param beforeId if > 0, only messages with id < beforeId (for loading older messages)
     */
    public static List<Message> getMessages(long conversationId, int limit, long beforeId) {
        List<Message> messages = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT m.id, m.conversation_id, m.sender_id, m.content, m.message_type, " +
            "m.status, m.is_unsent, m.created_at, u.display_name " +
            "FROM messages m JOIN users u ON u.id = m.sender_id " +
            "WHERE m.conversation_id = ? AND m.is_unsent = FALSE "
        );
        if (beforeId > 0) {
            sql.append("AND m.id < ? ");
        }
        sql.append("ORDER BY m.created_at DESC LIMIT ?");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setLong(idx++, conversationId);
            if (beforeId > 0) {
                ps.setLong(idx++, beforeId);
            }
            ps.setInt(idx++, Math.max(1, Math.min(limit, 100)));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = new Message();
                    m.setId(rs.getLong("id"));
                    m.setConversationId(rs.getLong("conversation_id"));
                    m.setSenderId(rs.getLong("sender_id"));
                    m.setContent(rs.getString("content"));
                    m.setMessageType(rs.getString("message_type"));
                    m.setStatus(rs.getString("status"));
                    m.setUnsent(rs.getBoolean("is_unsent"));
                    m.setCreatedAt(rs.getTimestamp("created_at"));
                    m.setSenderDisplayName(rs.getString("display_name"));
                    messages.add(m);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load messages for conv {}: {}", conversationId, e.getMessage());
        }
        return messages;
    }

    /**
     * Mark a message as unsent (soft delete).
     */
    public static boolean unsendMessage(long messageId) {
        String sql = "UPDATE messages SET is_unsent = TRUE WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to unsend message {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    /**
     * Update message delivery status.
     */
    public static boolean updateStatus(long messageId, String status) {
        String sql = "UPDATE messages SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update status for {}: {}", messageId, e.getMessage());
            return false;
        }
    }
}
