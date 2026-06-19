package com.messenger.server.service;

import com.messenger.server.db.DatabaseManager;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.FriendRequest;
import com.messenger.shared.model.User;
import com.messenger.shared.util.BCryptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private AuthService() {
        throw new UnsupportedOperationException("AuthService is a utility class");
    }

    public static class RegisterResult {
        private final boolean success;
        private final String message;
        private final User user;
        public RegisterResult(boolean success, String message, User user) {
            this.success = success; this.message = message; this.user = user;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
    }

    public static class LoginResult {
        private final boolean success;
        private final String message;
        private final User user;
        public LoginResult(boolean success, String message, User user) {
            this.success = success; this.message = message; this.user = user;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
    }

    public static RegisterResult register(String username, String password, String displayName) {
        if (username == null || username.trim().isEmpty()) {
            return new RegisterResult(false, "Username cannot be empty", null);
        }
        if (password == null || password.length() < 4) {
            return new RegisterResult(false, "Password must be at least 4 characters", null);
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = username;
        }

        String trimmedUsername = username.trim().toLowerCase();
        String hashedPassword = BCryptUtil.hashPassword(password);

        String sql = "INSERT INTO users (username, password_hash, display_name, presence) VALUES (?, ?, ?, 'OFFLINE')";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, trimmedUsername);
            ps.setString(2, hashedPassword);
            ps.setString(3, displayName.trim());
            int affected = ps.executeUpdate();

            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long userId = rs.getLong(1);
                        User user = new User();
                        user.setId(userId);
                        user.setUsername(trimmedUsername);
                        user.setDisplayName(displayName.trim());
                        user.setPresence(Protocol.PRESENCE_OFFLINE);
                        logger.info("User registered: {} (id={})", trimmedUsername, userId);
                        return new RegisterResult(true, "Registration successful", user);
                    }
                }
            }
            return new RegisterResult(false, "Registration failed", null);

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                logger.warn("Registration failed: username '{}' already exists", trimmedUsername);
                return new RegisterResult(false, "Username already exists", null);
            }
            logger.error("Database error during registration for '{}': {}", trimmedUsername, e.getMessage());
            return new RegisterResult(false, "Database error: " + e.getMessage(), null);
        }
    }

    public static LoginResult login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return new LoginResult(false, "Username cannot be empty", null);
        }
        if (password == null || password.isEmpty()) {
            return new LoginResult(false, "Password cannot be empty", null);
        }

        String trimmedUsername = username.trim().toLowerCase();
        String sql = "SELECT id, username, password_hash, display_name, avatar_url, presence FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, trimmedUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (BCryptUtil.checkPassword(password, storedHash)) {
                        User user = new User();
                        user.setId(rs.getLong("id"));
                        user.setUsername(rs.getString("username"));
                        user.setDisplayName(rs.getString("display_name"));
                        user.setAvatarUrl(rs.getString("avatar_url"));
                        user.setPresence(rs.getString("presence"));
                        logger.info("User logged in: {} (id={})", trimmedUsername, user.getId());
                        return new LoginResult(true, "Login successful", user);
                    } else {
                        logger.warn("Login failed for '{}': invalid password", trimmedUsername);
                        return new LoginResult(false, "Invalid username or password", null);
                    }
                } else {
                    logger.warn("Login failed for '{}': user not found", trimmedUsername);
                    return new LoginResult(false, "Invalid username or password", null);
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during login for '{}': {}", trimmedUsername, e.getMessage());
            return new LoginResult(false, "Database error: " + e.getMessage(), null);
        }
    }

    public static void updatePresence(long userId, String presence) {
        String sql = "UPDATE users SET presence = ?, last_seen = NOW() WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, presence);
            ps.setLong(2, userId);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                logger.debug("User {} presence updated to {}", userId, presence);
            }
        } catch (SQLException e) {
            logger.error("Failed to update presence for user {}: {}", userId, e.getMessage());
        }
    }

    public static List<User> searchUsers(String keyword) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, username, display_name, avatar_url, presence "
                   + "FROM users WHERE username LIKE ? OR display_name LIKE ? LIMIT 20";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setDisplayName(rs.getString("display_name"));
                    user.setAvatarUrl(rs.getString("avatar_url"));
                    user.setPresence(rs.getString("presence"));
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to search users: {}", e.getMessage());
        }
        return users;
    }

    public static User getUserById(long userId) {
        String sql = "SELECT id, username, display_name, avatar_url, presence FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setDisplayName(rs.getString("display_name"));
                    user.setAvatarUrl(rs.getString("avatar_url"));
                    user.setPresence(rs.getString("presence"));
                    return user;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    public static List<User> getFriends(long userId) {
        List<User> friends = new ArrayList<>();
        // New schema: friends table has user1_id and user2_id — check both directions
        String sql = "SELECT u.id, u.username, u.display_name, u.avatar_url, u.presence "
                   + "FROM users u INNER JOIN friends f ON (u.id = f.user2_id AND f.user1_id = ?) "
                   + "   OR (u.id = f.user1_id AND f.user2_id = ?) "
                   + "ORDER BY u.display_name";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setDisplayName(rs.getString("display_name"));
                    user.setAvatarUrl(rs.getString("avatar_url"));
                    user.setPresence(rs.getString("presence"));
                    friends.add(user);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get friends for user {}: {}", userId, e.getMessage());
        }
        return friends;
    }

    public static boolean addFriend(long user1Id, long user2Id) {
        long smaller = Math.min(user1Id, user2Id);
        long larger = Math.max(user1Id, user2Id);
        String sql = "INSERT IGNORE INTO friends (user1_id, user2_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, smaller);
            ps.setLong(2, larger);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to add friend {} <-> {}: {}", user1Id, user2Id, e.getMessage());
            return false;
        }
    }

    public static List<FriendRequest> getPendingFriendRequests(long userId) {
        List<FriendRequest> requests = new ArrayList<>();
        String sql = "SELECT fr.id, fr.sender_id, fr.receiver_id, fr.status, fr.created_at, " +
                     "u.username, u.display_name " +
                     "FROM friend_requests fr JOIN users u ON u.id = fr.sender_id " +
                     "WHERE fr.receiver_id = ? AND fr.status = 'PENDING' ORDER BY fr.created_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FriendRequest fr = new FriendRequest();
                    fr.setId(rs.getLong("id"));
                    fr.setSenderId(rs.getLong("sender_id"));
                    fr.setReceiverId(rs.getLong("receiver_id"));
                    fr.setStatus(rs.getString("status"));
                    fr.setCreatedAt(rs.getTimestamp("created_at"));
                    fr.setSenderUsername(rs.getString("username"));
                    fr.setSenderDisplayName(rs.getString("display_name"));
                    requests.add(fr);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get pending requests for {}: {}", userId, e.getMessage());
        }
        return requests;
    }

    public static boolean updateProfile(long userId, String displayName, String avatarUrl) {
        StringBuilder sql = new StringBuilder("UPDATE users SET ");
        List<Object> params = new ArrayList<>();
        if (displayName != null && !displayName.trim().isEmpty()) {
            sql.append("display_name = ?, ");
            params.add(displayName.trim());
        }
        if (avatarUrl != null) {
            sql.append("avatar_url = ?, ");
            params.add(avatarUrl);
        }
        if (params.isEmpty()) return false;
        sql.setLength(sql.length() - 2); // remove trailing ", "
        sql.append(" WHERE id = ?");
        params.add(userId);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update profile for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public static boolean removeFriend(long user1Id, long user2Id) {
        long smaller = Math.min(user1Id, user2Id);
        long larger = Math.max(user1Id, user2Id);
        String sql = "DELETE FROM friends WHERE user1_id = ? AND user2_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, smaller);
            ps.setLong(2, larger);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to remove friend {} <-> {}: {}", user1Id, user2Id, e.getMessage());
            return false;
        }
    }

    public static void sendFriendRequest(long senderId, long receiverId) {
        String sql = "INSERT IGNORE INTO friend_requests (sender_id, receiver_id, status) VALUES (?, ?, 'PENDING')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, senderId);
            ps.setLong(2, receiverId);
            ps.executeUpdate();
            logger.info("Friend request sent: {} -> {}", senderId, receiverId);
        } catch (SQLException e) {
            logger.error("Failed to send friend request {} -> {}: {}", senderId, receiverId, e.getMessage());
        }
    }

    public static void updateFriendRequestStatus(long senderId, long receiverId, String status) {
        String sql = "UPDATE friend_requests SET status = ? WHERE sender_id = ? AND receiver_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, senderId);
            ps.setLong(3, receiverId);
            ps.executeUpdate();
            logger.info("Friend request {} -> {} updated to {}", senderId, receiverId, status);
        } catch (SQLException e) {
            logger.error("Failed to update friend request status: {}", e.getMessage());
        }
    }
}
