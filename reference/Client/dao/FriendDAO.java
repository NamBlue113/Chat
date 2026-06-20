package Client.dao;

import database.Database;
import Client.model.UserSearchResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {

    public List<String> getFriends(String currentUser) {

        List<String> friends = new ArrayList<>();

        try {

            Connection conn = Database.connect();

            String sql =
                    "SELECT CASE " +
                            "WHEN user1=? THEN user2 " +
                            "ELSE user1 END AS friend " +
                            "FROM friends " +
                            "WHERE user1=? OR user2=?";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, currentUser);
            ps.setString(2, currentUser);
            ps.setString(3, currentUser);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                friends.add(
                        rs.getString("friend")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return friends;
    }

    public boolean addFriend(String currentUser,
                             String friend) {

        try {

            if (currentUser.equals(friend)) {
                return false;
            }

            Connection conn = Database.connect();

            String check =
                    "SELECT * FROM friends " +
                            "WHERE (user1=? AND user2=?) " +
                            "OR (user1=? AND user2=?)";

            PreparedStatement psCheck =
                    conn.prepareStatement(check);

            psCheck.setString(1, currentUser);
            psCheck.setString(2, friend);
            psCheck.setString(3, friend);
            psCheck.setString(4, currentUser);

            ResultSet rs = psCheck.executeQuery();

            if (rs.next()) {
                return false;
            }

            String sql =
                    "INSERT INTO friends(user1,user2) " +
                            "VALUES(?,?)";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, currentUser);
            ps.setString(2, friend);

            ps.executeUpdate();

            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public List<UserSearchResult> searchUsers(
            String keyword,
            String type,
            String currentUser) {

        List<UserSearchResult> results =
                new ArrayList<>();

        try {

            Connection conn = Database.connect();

            String sql;

            if ("phone".equalsIgnoreCase(type)) {

                sql =
                        "SELECT username, phone " +
                                "FROM users " +
                                "WHERE phone LIKE ? " +
                                "AND username <> ?";

            } else {

                sql =
                        "SELECT username, phone " +
                                "FROM users " +
                                "WHERE username LIKE ? " +
                                "AND username <> ?";
            }

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, currentUser);

            ResultSet rs =
                    ps.executeQuery();

            while (rs.next()) {

                results.add(
                        new UserSearchResult(
                                rs.getString("username"),
                                rs.getString("phone")
                        )
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }
}