package Client.dao;

import Client.model.ChatItem;
import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ChatDAO {

    public List<ChatItem> getConversation(
            String user1,
            String user2) {

        List<ChatItem> list =
                new ArrayList<>();

        try {

            Connection conn =
                    Database.connect();

            String sql =

                    "SELECT sender, content, sent_time, " +
                            "'MESSAGE' AS type, " +
                            "NULL AS file_name, " +
                            "NULL AS file_data " +

                            "FROM messages " +

                            "WHERE (sender=? AND receiver=?) " +
                            "OR (sender=? AND receiver=?) " +

                            "UNION ALL " +

                            "SELECT sender, NULL, sent_time, " +
                            "'FILE' AS type, " +
                            "file_name, " +
                            "file_data " +

                            "FROM files " +

                            "WHERE (sender=? AND receiver=?) " +
                            "OR (sender=? AND receiver=?) " +

                            "ORDER BY sent_time";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, user1);
            ps.setString(2, user2);

            ps.setString(3, user2);
            ps.setString(4, user1);

            ps.setString(5, user1);
            ps.setString(6, user2);

            ps.setString(7, user2);
            ps.setString(8, user1);

            ResultSet rs =
                    ps.executeQuery();

            while (rs.next()) {

                list.add(
                        new ChatItem(
                                rs.getString("type"),
                                rs.getString("sender"),
                                rs.getString("content"),
                                rs.getString("file_name"),
                                rs.getBytes("file_data"),
                                rs.getTimestamp("sent_time")
                                        .toLocalDateTime()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                        )
                        )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return list;
    }
}