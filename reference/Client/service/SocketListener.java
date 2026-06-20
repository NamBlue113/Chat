package Client.service;

public interface SocketListener {

    void onMessageReceived(
            String sender,
            String content
    );

    void onFileReceived(
            String sender,
            String fileName,
            byte[] data
    );

    void onAudioFrame(
            String sender,
            byte[] audioData
    );


    void onCallReceived(String sender);

    void onCallAccepted(String sender);

    void onCallEnded(String sender);

    void onVideoFrame(
            String sender,
            byte[] imageData
    );
}