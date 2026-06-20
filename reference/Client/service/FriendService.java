package Client.service;

import Client.dao.FriendDAO;
import Client.model.UserSearchResult;
import database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class FriendService {

    private final FriendDAO dao =
            new FriendDAO();

    public List<String> getFriends(
            String currentUser) {

        return dao.getFriends(
                currentUser
        );
    }

    public boolean addFriend(
            String currentUser,
            String friend) {

        return dao.addFriend(
                currentUser,
                friend
        );
    }

    public List<UserSearchResult> searchUsers(
            String keyword,
            String type,
            String currentUser) {

        return dao.searchUsers(
                keyword,
                type,
                currentUser
        );
    }

    public boolean updateAccount(
            String oldUser,
            String newUser,
            String email,
            String phone,
            String password
    ) {

        try {

            Connection con =
                    Database.connect();

            PreparedStatement ps =
                    con.prepareStatement(
                            """
                            UPDATE users
                            SET username=?,
                                email=?,
                                phone=?,
                                password=?
                            WHERE username=?
                            """
                    );

            ps.setString(1,newUser);
            ps.setString(2,email);
            ps.setString(3,phone);
            ps.setString(4,password);
            ps.setString(5,oldUser);

            return ps.executeUpdate() > 0;

        } catch(Exception e) {

            e.printStackTrace();
        }

        return false;
    }
}