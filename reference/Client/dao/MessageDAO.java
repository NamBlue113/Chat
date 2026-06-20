package Client.dao;

import database.Database;
import Client.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public void save(Message message) {

        try {

            Connection conn = Database.connect();

            String sql =
                    "INSERT INTO messages(sender,receiver,content) " +
                            "VALUES(?,?,?)";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, message.getSender());
            ps.setString(2, message.getReceiver());
            ps.setString(3, message.getContent());

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Message> getConversation(
            String user1,
            String user2) {

        List<Message> list =
                new ArrayList<>();

        try {

            Connection conn =
                    Database.connect();

            String sql =
                    "SELECT sender,receiver,content " +
                            "FROM messages " +
                            "WHERE (sender=? AND receiver=?) " +
                            "OR (sender=? AND receiver=?) " +
                            "ORDER BY sent_time";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, user1);
            ps.setString(2, user2);

            ps.setString(3, user2);
            ps.setString(4, user1);

            ResultSet rs =
                    ps.executeQuery();

            while (rs.next()) {

                list.add(
                        new Message(
                                rs.getString("sender"),
                                rs.getString("receiver"),
                                rs.getString("content")
                        )
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}