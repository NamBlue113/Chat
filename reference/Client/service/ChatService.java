package Client.service;

import Client.dao.ChatDAO;
import Client.model.ChatItem;

import java.util.List;

public class ChatService {

    private final ChatDAO dao =
            new ChatDAO();

    public List<ChatItem> getConversation(
            String user1,
            String user2) {

        return dao.getConversation(
                user1,
                user2
        );
    }
}