# Hướng dẫn thêm tính năng gọi video vào project Java

## Tổng quan

Tính năng gọi video được extract từ project **chattest**. Kiến trúc sử dụng TCP socket với `DataInputStream`/`DataOutputStream`, truyền frame video dạng JPEG và âm thanh PCM qua relay server.

## File đã extract

| File | Vai trò |
|---|---|
| `VideoCallService.java` | Client-side: gửi/nhận tín hiệu + capture webcam/audio + phát audio |
| `VideoCallServerHandler.java` | Server-side: relay message giữa 2 client |

---

## 1. Server-side integration

### a) Cấu trúc server cần có

Server cần một `Map<String, DataOutputStream>` lưu output stream của từng user đã login.

```java
// Trong class server của bạn
private static final Map<String, DataOutputStream> clients = new HashMap<>();

// Khi user login
DataInputStream in = new DataInputStream(socket.getInputStream());
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
String username = in.readUTF();
clients.put(username, out);
```

### b) Trong vòng lặp nhận message

```java
String type = in.readUTF();

switch (type) {
    case "CALL":
        VideoCallServerHandler.handleCall(username, in, clients);
        break;
    case "CALL_ACCEPT":
        VideoCallServerHandler.handleCallAccept(username, in, clients);
        break;
    case "CALL_END":
        VideoCallServerHandler.handleCallEnd(username, in, clients);
        break;
    case "VIDEO":
        VideoCallServerHandler.handleVideoFrame(username, in, clients);
        break;
    case "AUDIO_FRAME":
        VideoCallServerHandler.handleAudioFrame(username, in, clients);
        break;
}
```

---

## 2. Client-side integration

### a) Yêu cầu dependency

Trong `pom.xml` (Maven):

```xml
<dependency>
    <groupId>com.github.sarxos</groupId>
    <artifactId>webcam-capture</artifactId>
    <version>0.3.12</version>
</dependency>
```

Hoặc download jar từ: https://github.com/sarxos/webcam-capture/releases

### b) Kết nối socket

```java
Socket socket = new Socket("server_ip", 5001);
DataInputStream in = new DataInputStream(socket.getInputStream());
DataOutputStream out = new DataOutputStream(socket.getOutputStream());

// Gửi username sau khi connect
out.writeUTF(username);
out.flush();
```

### c) Khởi tạo VideoCallService

```java
VideoCallService vcService = new VideoCallService(socket, username);

vcService.setListener(new VideoCallService.VideoCallListener() {
    @Override
    public void onCallReceived(String sender) {
        // Hiển thị dialog: "sender đang gọi cho bạn"
        // Nếu user đồng ý:
        //   vcService.acceptCall(sender);
        //   mở UI cuộc gọi
        //   vcService.initWebcam();
        //   vcService.initAudio();
        //   vcService.startCapture(sender);
    }

    @Override
    public void onCallAccepted(String sender) {
        // Đối phương đã chấp nhận cuộc gọi
        // Mở UI cuộc gọi
        // vcService.initWebcam();
        // vcService.initAudio();
        // vcService.startCapture(sender);
    }

    @Override
    public void onCallEnded(String sender) {
        // Đối phương kết thúc cuộc gọi
        // vcService.stopCapture();
        // Đóng UI cuộc gọi
    }

    @Override
    public void onVideoFrame(String sender, byte[] jpegData) {
        // Nhận frame video từ đối phương
        // Chuyển byte[] JPEG thành ImageIcon và hiển thị
        // BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegData));
        // remoteLabel.setIcon(new ImageIcon(img));
    }

    @Override
    public void onAudioFrame(String sender, byte[] pcmData) {
        // Nhận audio từ đối phương, phát ra loa
        vcService.playAudio(pcmData);
    }
});
```

### d) Tích hợp vào vòng lặp nhận socket chính

```java
// Trong thread đọc socket
while (true) {
    String type = in.readUTF();

    // Gọi dispatch — nó xử lý tất cả message call
    vcService.dispatchIncoming(type);
}
```

---

## 3. Protocol (wire format)

Tất cả message đều gửi qua `DataOutputStream`, đọc qua `DataInputStream`.

### CALL (gửi từ caller)
```
writeUTF("CALL")
writeUTF(receiver_username)
flush()
```
Server relay:
```
writeUTF("CALL")
writeUTF(sender_username)
flush()
```

### CALL_ACCEPT (gửi từ callee khi accept)
```
writeUTF("CALL_ACCEPT")
writeUTF(caller_username)
flush()
```

### CALL_END (gửi từ ai kết thúc trước)
```
writeUTF("CALL_END")
writeUTF(other_party_username)
flush()
```

### VIDEO (gửi ~30 lần/giây khi đang gọi)
```
writeUTF("VIDEO")
writeUTF(receiver_username)
writeLong(jpegData.length)
write(jpegData)          // raw JPEG bytes
flush()
```

### AUDIO_FRAME (gửi liên tục khi đang gọi)
```
writeUTF("AUDIO_FRAME")
writeUTF(receiver_username)
writeInt(pcmData.length)
write(pcmData, 0, length)
flush()
```

---

## 4. Luồng cuộc gọi điển hình

```
Caller                          Server                    Callee
  │                               │                         │
  │── sendCall(callee) ──────────>│                         │
  │                               │── relay "CALL" ───────>│
  │                               │                         │── hiện dialog
  │                               │                         │── user OK?
  │                               │<── acceptCall(caller) ──│
  │<── onCallAccepted() ─────────│                         │
  │                               │                         │
  │── initWebcam() + initAudio()  │     initWebcam() + initAudio()
  │── startCapture(callee)        │     startCapture(caller)
  │                               │                         │
  │── VIDEO/AUDIO frames ───────>│── relay ───────────────>│
  │<── VIDEO/AUDIO frames ──────│<── relay ───────────────│
  │                               │                         │
  │── endCall(callee) ──────────>│── relay CALL_END ──────>│
  │── stopCapture()               │     stopCapture()
```

---

## 5. Lưu ý

- **Audio format**: PCM 44100 Hz, 16-bit, mono, signed, little-endian
- **Video resolution**: Webcam capture được resize xuống 320×240 trước khi gửi
- **Frame rate**: Video ~33 FPS (`Thread.sleep(30)`)
- **Audio buffer**: 1024 bytes mỗi frame
- **Thread safety**: Các method gửi đều dùng `synchronized (out)` để tránh xung đột luồng
- **Dependency webcam**: Project gốc dùng thư viện [webcam-capture](https://github.com/sarxos/webcam-capture) — cần thêm vào classpath
- **Không có "gọi thường" riêng**: Tính năng này luôn gồm cả video + audio. Nếu cần voice-only, phải tách phần audio capture ra khỏi VideoCallService
