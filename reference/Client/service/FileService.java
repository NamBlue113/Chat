package Client.service;

import Client.dao.FileDAO;
import Client.model.ChatFile;

import java.util.List;

public class FileService {

    private final FileDAO dao =
            new FileDAO();

    public void saveFile(
            String sender,
            String receiver,
            String fileName,
            byte[] data){

        dao.save(
                new ChatFile(
                        sender,
                        receiver,
                        fileName,
                        data
                )
        );
    }

    public List<ChatFile> getFiles(
            String user1,
            String user2){

        return dao.getFiles(
                user1,
                user2
        );
    }
}