package Client.service;

import Client.dao.MessageDAO;
import Client.model.Message;

import java.util.List;

public class MessageService {

    private final MessageDAO dao =
            new MessageDAO();

    public void saveMessage(
            String sender,
            String receiver,
            String content){

        dao.save(
                new Message(
                        sender,
                        receiver,
                        content
                )
        );
    }

    public List<Message> getConversation(
            String user1,
            String user2){

        return dao.getConversation(
                user1,
                user2
        );
    }
}