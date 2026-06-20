package Client.dao;

import database.Database;
import Client.model.ChatFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class FileDAO {

    public void save(ChatFile file) {

        try {

            Connection conn =
                    Database.connect();

            String sql =
                    "INSERT INTO files(sender,receiver,file_name,file_data) " +
                            "VALUES(?,?,?,?)";

            PreparedStatement ps =
                    conn.prepareStatement(sql);

            ps.setString(1, file.getSender());
            ps.setString(2, file.getReceiver());
            ps.setString(3, file.getFileName());
            ps.setBytes(4, file.getData());

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ChatFile> getFiles(
            String user1,
            String user2) {

        List<ChatFile> files =
                new ArrayList<>();

        try {

            Connection conn =
                    Database.connect();

            String sql =
                    "SELECT * FROM files " +
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

                files.add(
                        new ChatFile(
                                rs.getString("sender"),
                                rs.getString("receiver"),
                                rs.getString("file_name"),
                                rs.getBytes("file_data")
                        )
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return files;
    }
}