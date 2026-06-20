# Moji Messenger — Desktop Chat Application

> Messenger / Zalo clone — Java 17 + JavaFX + MySQL  
> Version 1.0.0 — Last updated: 2026-06-19

---

## Mục Lục

1. [Tổng Quan Dự Án](#1-tổng-quan-dự-án)
2. [Tech Stack & Phiên Bản](#2-tech-stack--phiên-bản)
3. [Kiến Trúc Hệ Thống](#3-kiến-trúc-hệ-thống)
4. [Cấu Trúc Thư Mục](#4-cấu-trúc-thư-mục)
5. [Quy Tắc Coding](#5-quy-tắc-coding)
6. [Giao Thức Truyền Tin (Protocol)](#6-giao-thức-truyền-tin-protocol)
7. [Luồng Dữ Liệu (Data Flow)](#7-luồng-dữ-liệu-data-flow)
8. [Database Schema](#8-database-schema)
9. [Các Thành Phần Server](#9-các-thành-phần-server)
10. [Các Thành Phần Client](#10-các-thành-phần-client)
11. [Tính Năng Hiện Có](#11-tính-năng-hiện-có)
12. [Hướng Dẫn Build & Chạy](#12-hướng-dẫn-build--chạy)
13. [Cấu Hình](#13-cấu-hình)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Tổng Quan Dự Án

**Moji Messenger** là ứng dụng chat desktop đầy đủ tính năng, được xây dựng theo mô hình client-server. Mục tiêu là tái tạo các tính năng cốt lõi của Messenger/Zalo bao gồm:

- Nhắn tin 1-1 và nhóm theo thời gian thực
- Gọi voice và video qua UDP relay
- Gửi file, hình ảnh, sticker
- Quản lý bạn bè, nhóm chat
- Giao diện Dark/Light theme

**Đây là đồ án tốt nghiệp** — không sử dụng các framework web (Spring, etc.), toàn bộ server và client được viết thuần Java.

---

## 2. Tech Stack & Phiên Bản

| Tầng | Công nghệ | Phiên bản |
|------|-----------|-----------|
| Ngôn ngữ | Java | 17 (LTS) |
| UI | JavaFX Controls / Graphics / FXML / Media | 17.0.6 |
| Build | Maven | 3.8+ |
| WebSocket (server) | java-websocket (TooTallNate) | 1.5.3 |
| Database | MySQL | 8.x |
| Connection pool | HikariCP | 5.0.1 |
| Password hash | jBCrypt | 0.4 |
| JSON | Gson | 2.10.1 |
| Webcam | Sarxos webcam-capture | 0.3.12 |
| Logging | SLF4J API + Logback | 2.0.7 / 1.4.7 |
| HTTP Client | java.net.http.HttpClient | Java 11+ built-in |
| Testing | JUnit Jupiter | 5.9.3 |

> **Lưu ý JavaFX**: Phải dùng `mvn javafx:run` để chạy client, **không dùng** `java -jar` vì JavaFX yêu cầu module path riêng.

---

## 3. Kiến Trúc Hệ Thống

```
+-------------+   WebSocket ws://host:9080   +--------------+   JDBC   +-----------+
|  Client A   | <--------------------------> |  WsChatServer| <------> | MySQL 8.x |
| (JavaFX UI) |                             |   (port 9080)|          | gui_chat  |
+------+------+   TCP (legacy) port 9000    +------+-------+          +-----------+
       |          <------------------------->      |
       |                                    | ChatServer.java |
       | UDP port 9001 (voice PCM)          | (khởi động cả  |
       | UDP port 9002 (video JPEG)         |  3 service)    |
       +---> MediaRelayServer <-------------+
             (TURN-like UDP relay)
```

### Phân Tầng

```
[Client Side]                          [Server Side]
─────────────────────────────          ──────────────────────────────
client.ui.*        ← Presentation      server.service.*  ← Business Logic
    ↕  JavaFX / JSON                       ↕  JDBC / HikariCP
client.net.*       ← Transport         server.db.*       ← Data Access
    ↕  WebSocket / TCP                     ↕  MySQL
shared.Protocol    ← Giao thức chung   shared.model.*    ← Entity dùng 2 phía
```

### Hai Transport Song Song

| Transport | Port | Class | Trạng thái |
|-----------|------|-------|------------|
| WebSocket (JSON) | 9080 | `WsChatServer.java` | **Mặc định** (ưu tiên) |
| TCP (JSON) | 9000 | `ClientHandler.java` | Legacy, vẫn hoạt động |

Client tự động kết nối WebSocket trước. Nếu thất bại mới fallback sang TCP.

---

## 4. Cấu Trúc Thư Mục

```
src/main/java/com/messenger/
├── client/
│   ├── call/
│   │   ├── VoiceCallHandler.java     # Gọi voice: PCM 16kHz, noise gate, AGC, jitter buffer, UDP
│   │   ├── VideoCallHandler.java     # Gọi video: Sarxos webcam → JPEG → UDP relay
│   │   └── VideoStreamHandler.java   # Render video frame từ remote
│   ├── config/
│   │   └── AppConfig.java            # Đọc app.properties, cung cấp URL server
│   ├── net/
│   │   ├── RealtimeClient.java       # WebSocket client (java.net.http), auto-reconnect
│   │   └── ApiClient.java            # HTTP GET/POST helper dùng java.net.http.HttpClient
│   ├── ui/
│   │   ├── LoginView.java            # Màn hình đăng nhập
│   │   ├── RegisterView.java         # Màn hình đăng ký
│   │   ├── MainView.java             # Layout chính: NavRail 68px + Sidebar 320px + Content + InfoPanel 280px
│   │   ├── NotificationManager.java  # Desktop notification
│   │   ├── IconManager.java          # Load icon từ resources
│   │   ├── StickerManager.java       # Mã hoá/giải mã sticker
│   │   ├── ThemeManager.java         # Chuyển Dark/Light CSS
│   │   └── tabs/
│   │       ├── ChatTab.java          # Tab chat: danh sách hội thoại + vùng nhắn tin (~1888 dòng)
│   │       ├── ContactsTab.java      # Tab danh bạ: friend requests + danh sách bạn bè
│   │       └── ProfileTab.java       # Tab hồ sơ người dùng
│   ├── ChatClient.java               # JavaFX Application — entry point client
│   ├── ChatHistoryManager.java       # Cache tin nhắn local (JSON file trong chat_history/)
│   └── ServerConnection.java         # TCP transport (legacy)
│
├── server/
│   ├── db/
│   │   └── DatabaseManager.java      # HikariCP pool, khởi tạo schema từ sql/schema.sql
│   ├── service/
│   │   ├── AuthService.java          # Đăng ký (BCrypt), đăng nhập, profile, bạn bè, presence
│   │   ├── ConversationService.java  # CRUD hội thoại (private/group), danh sách với last message
│   │   ├── GroupService.java         # Tạo nhóm, thêm/xóa/promote/demote thành viên, tin nhắn nhóm
│   │   ├── MessageService.java       # Lưu/lấy tin nhắn, phân trang (beforeId), unsend, delivery
│   │   └── FileService.java          # Upload chunked, reassembly, download, xóa file
│   ├── ChatServer.java               # Main server: khởi động TCP 9000 + WS 9080 + UDP relay
│   ├── ClientHandler.java            # TCP handler: parse JSON line, dispatch ~40 message types
│   ├── WsChatServer.java             # WebSocket handler: mirror logic của ClientHandler
│   └── MediaRelayServer.java         # UDP TURN-like relay: port 9001 (voice), 9002 (video)
│
└── shared/
    ├── model/
    │   ├── Message.java              # Entity tin nhắn
    │   ├── User.java                 # Entity người dùng
    │   ├── Group.java                # Entity nhóm + inner class GroupMember
    │   └── FriendRequest.java        # Entity lời mời kết bạn
    ├── util/
    │   ├── BCryptUtil.java           # Wrapper hash/verify password
    │   └── FileTransferUtil.java     # Helper chia/ghép chunk file
    └── Protocol.java                 # TẤT CẢ constants: message types, status codes, ports

src/main/resources/
├── sql/schema.sql                    # DDL 17 bảng (DROP/CREATE/USE bị lọc khi runtime init)
├── css/
│   ├── dark-theme.css                # CSS dark mode cho JavaFX
│   └── light-theme.css              # CSS light mode cho JavaFX
├── app.properties                    # server.baseUrl (local hoặc ngrok)
└── logback.xml                       # Cấu hình log: file rolling 30 ngày + console
```

---

## 5. Quy Tắc Coding

### 5.1 Quy Ước Đặt Tên

| Đối tượng | Quy tắc | Ví dụ |
|-----------|---------|-------|
| Class / Interface | PascalCase | `ChatServer`, `MessageService` |
| Method | camelCase | `handleDirectMessage()`, `getUserConversations()` |
| Field / Variable | camelCase | `onlineClients`, `userId` |
| Constant (`static final`) | SCREAMING_SNAKE_CASE | `TCP_PORT`, `FILE_CHUNK_SIZE` |
| Package | lowercase, phân cấp theo vai trò | `com.messenger.server.service` |
| Resource file | kebab-case | `dark-theme.css`, `schema.sql` |

### 5.2 Package & Trách Nhiệm

Mỗi package có trách nhiệm rõ ràng — **không được vi phạm ranh giới**:

```
shared.*          → Code dùng chung cả client lẫn server. Không import từ client.* hoặc server.*
server.service.*  → Business logic. Không gọi trực tiếp socket/UI
server.db.*       → Chỉ quản lý connection pool. Không chứa business logic
client.net.*      → Chỉ xử lý transport (WebSocket/HTTP). Không cập nhật UI trực tiếp
client.ui.*       → Chỉ JavaFX. Mọi thao tác UI phải chạy trên JavaFX Application Thread
```

### 5.3 Luồng JavaFX (Thread Safety)

> **Quy tắc bắt buộc**: Mọi thao tác cập nhật UI (thêm node, setText, setStyle...) **phải** thực hiện trong `Platform.runLater()`.

```java
// ĐỌC dữ liệu từ background thread → OK
String msg = parseJson(raw);

// CẬP NHẬT UI từ background thread → BẮT BUỘC dùng Platform.runLater
Platform.runLater(() -> {
    messageListView.getItems().add(msg);
});
```

Các callback WebSocket (`RealtimeClient`) và handler TCP (`ClientHandler`) đều chạy trên thread riêng — không bao giờ gọi trực tiếp JavaFX node từ đó.

### 5.4 JSON Protocol

- Tất cả message type dùng **constant từ `Protocol.java`**, không hardcode string.
- Format request/response: `JsonObject` với Gson, không dùng Map/HashMap khi serialize gửi đi.

```java
// ĐÚNG
JsonObject req = new JsonObject();
req.addProperty("type", Protocol.TYPE_MESSAGE);

// SAI — hardcode string dễ lỗi typo
req.addProperty("type", "MESSAGE");
```

### 5.5 Database Access

- Luôn dùng `PreparedStatement` cho mọi câu query có tham số. **Không nối chuỗi SQL** (SQL injection).
- Lấy connection từ `DatabaseManager.getConnection()` (HikariCP pool) trong khối `try-with-resources`.

```java
// ĐÚNG
try (Connection conn = DatabaseManager.getConnection();
     PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
    ps.setLong(1, userId);
    ResultSet rs = ps.executeQuery();
    ...
}

// SAI — SQL injection
String sql = "SELECT * FROM users WHERE id = " + userId;
```

### 5.6 Logging

- Dùng SLF4J `Logger`, không `System.out.println`.
- Log ở đúng mức: `debug` cho flow bình thường, `info` cho sự kiện quan trọng, `warn` cho trường hợp bất thường, `error` cho exception.

```java
private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

logger.info("User {} logged in", username);
logger.error("Failed to save message", e);
```

### 5.7 Error Handling

- Service method trả lỗi về client qua JSON response có `code` và `message`, không throw exception ra ngoài client.
- Các exception kết nối / IO trong handler thì log và đóng connection, không để crash server.

### 5.8 Constants & Magic Numbers

Mọi port, timeout, kích thước chunk, giới hạn file đều khai báo trong `Protocol.java`. Không đặt magic number trực tiếp trong code:

```java
// ĐÚNG
socket.setSoTimeout(Protocol.SOCKET_TIMEOUT_MS);
byte[] buf = new byte[Protocol.FILE_CHUNK_SIZE];

// SAI
socket.setSoTimeout(30000);
byte[] buf = new byte[8192];
```

### 5.9 Thêm Message Type Mới

Khi muốn thêm loại message mới, thực hiện theo thứ tự sau:

1. **`Protocol.java`** — Khai báo constant `TYPE_XXX = "XXX"`
2. **`ClientHandler.java`** — Thêm `case Protocol.TYPE_XXX:` trong switch, gọi `handleXxx(data)`
3. **`WsChatServer.java`** — Thêm case tương tự (hai file phải đồng bộ)
4. **`service/XxxService.java`** — Viết business logic
5. **Client** — Gửi request và xử lý response tương ứng trong `ChatClient.handleServerMessage()`

---

## 6. Giao Thức Truyền Tin (Protocol)

### 6.1 Định Dạng JSON

**Request từ client:**
```json
{
  "type": "MESSAGE",
  "data": {
    "receiverId": 42,
    "content": "Xin chào!",
    "messageType": "TEXT"
  }
}
```

**Response từ server:**
```json
{
  "type": "SUCCESS",
  "code": 200,
  "data": {
    "messageId": 1001,
    "timestamp": "2026-06-19T10:30:00"
  }
}
```

**Lỗi:**
```json
{
  "type": "ERROR",
  "code": 401,
  "message": "Unauthorized"
}
```

### 6.2 Response Codes

| Code | Ý Nghĩa |
|------|---------|
| 200 | Thành công |
| 400 | Lỗi request (dữ liệu sai) |
| 401 | Chưa đăng nhập / không có quyền |
| 404 | Không tìm thấy |
| 409 | Xung đột (user đã tồn tại) |
| 410 | User offline |
| 411 | Đã là bạn bè rồi |

### 6.3 Danh Sách Message Types

**Authentication:**
`REGISTER` | `LOGIN` | `LOGOUT` | `UPDATE_PROFILE`

**Chat:**
`MESSAGE` | `GROUP_MESSAGE` | `GET_MESSAGE_HISTORY` | `TYPING` | `MESSAGE_STATUS` | `UNSEND` | `REACTION`

**Friends:**
`SEARCH_USER` | `FRIEND_REQUEST` | `FRIEND_RESPONSE` | `FRIEND_LIST` | `GET_FRIEND_REQUESTS` | `PRESENCE`

**Groups:**
`GROUP_CREATE` | `GROUP_JOIN` | `GROUP_LEAVE` | `GROUP_MESSAGE` | `GROUP_LIST` | `GROUP_INFO` | `GROUP_UPDATE` | `GROUP_REMOVE_MEMBER` | `GROUP_PROMOTE` | `GROUP_DEMOTE`

**Conversations:**
`CONVERSATION_LIST` | `CONVERSATION_DELETE`

**Files:**
`FILE_TRANSFER` | `FILE_CHUNK`

**Calls:**
`VOICE_CALL_START` | `VOICE_CALL_ACCEPT` | `VOICE_CALL_REJECT` | `VOICE_CALL_END`  
`VIDEO_CALL_START` | `VIDEO_CALL_ACCEPT` | `VIDEO_CALL_REJECT` | `VIDEO_CALL_END`  
`RELAY_INFO` | `VIDEO_FRAME` | `VOICE_DATA`

### 6.4 Content Types (messageType)

`TEXT` | `IMAGE` | `FILE` | `VIDEO` | `VOICE` | `STICKER`

### 6.5 Delimiter

Mỗi JSON message kết thúc bằng `\n` (newline). Server đọc từng dòng qua `BufferedReader.readLine()`.

---

## 7. Luồng Dữ Liệu (Data Flow)

### 7.1 Kết Nối

```
Client khởi động
  → đọc app.properties → lấy server.baseUrl
  → RealtimeClient kết nối WebSocket ws://host:9080
  → Nếu thất bại: fallback TCP port 9000
  → Sau khi kết nối: gửi LOGIN
  → Nhận SUCCESS → tự động tải FRIEND_LIST, CONVERSATION_LIST, GROUP_LIST
```

### 7.2 Tin Nhắn 1-1

```
A gõ tin → ChatTab gọi send() → RealtimeClient.send(json)
  → Server WsChatServer nhận
  → ClientHandler.handleDirectMessage()
  → MessageService.saveMessage() → MySQL
  → Nếu B online: forward JsonObject tới B's socket
  → Gửi MESSAGE_STATUS DELIVERED về A
```

### 7.3 Tin Nhắn Nhóm

```
A gửi GROUP_MESSAGE → Server
  → GroupService.saveGroupMessage() → MySQL
  → Lặp qua tất cả member online của nhóm → forward
```

### 7.4 Cuộc Gọi Video

```
A: prepareForCall() → mở UDP socket
A: gửi VIDEO_CALL_START (calleeId) → Server
Server: forward tới B
B: chấp nhận → VIDEO_CALL_ACCEPT → Server
Server: MediaRelayServer.createSession() → gán port relay
Server: gửi RELAY_INFO (sessionId, voicePort, videoPort) → cả A và B
A+B: gửi JPEG frame → UDP port 9002 → relay → phía kia render
```

### 7.5 Transfer File (Chunked)

```
Client: chia file thành chunk 8KB (Protocol.FILE_CHUNK_SIZE)
Client: gửi FILE_TRANSFER (tên file, số chunk, kích thước)
Client: gửi từng FILE_CHUNK (fileId, chunkIndex, data Base64)
Server: FileService tích lũy chunk → ghép lại khi đủ
Server: lưu đường dẫn vào DB → notify receiver
```

---

## 8. Database Schema

Database: `gui_chat`, charset `utf8mb4_unicode_ci` — tự động tạo khi server khởi động.

### 8.1 Sơ Đồ Quan Hệ (tóm tắt)

```
users ──< friend_requests (sender_id, receiver_id)
users ──< friends (user1_id, user2_id)
users ──< blocks (blocker_id, blocked_id)
users ──< user_settings (1-1)
users ──< login_history

conversations ──< conversation_members >── users
conversations ──< messages ──< attachments ──< file_chunks
                  messages ──< message_reactions >── users
                  messages ──< message_reads >── users

conversations ──< call_sessions ──< call_participants >── users
```

### 8.2 Bảng Chính

| Bảng | Mục Đích | Cột Quan Trọng |
|------|----------|----------------|
| `users` | Tài khoản | `username` UNIQUE, `password_hash`, `presence` ENUM, `last_seen` |
| `user_settings` | Cài đặt | `theme` ENUM(LIGHT/DARK), `language_code`, `notification_enabled` |
| `login_history` | Audit | `ip_address`, `device_info`, `login_time`, `logout_time` |
| `friend_requests` | Lời mời kết bạn | `sender_id`, `receiver_id`, `status` ENUM(PENDING/ACCEPTED/REJECTED) |
| `friends` | Cặp bạn bè | `user1_id`, `user2_id`, UNIQUE constraint |
| `blocks` | Danh sách chặn | `blocker_id`, `blocked_id` |
| `conversations` | Phòng chat | `type` ENUM(PRIVATE/GROUP), `title`, `owner_id` |
| `conversation_members` | Thành viên | `role` ENUM(ADMIN/MEMBER), UNIQUE(conversation_id, user_id) |
| `messages` | Tin nhắn | `message_type` ENUM, `status` ENUM, `is_unsent`, `reply_to_id` |
| `attachments` | File đính kèm | `file_name`, `file_path`, `mime_type`, `file_size` |
| `file_chunks` | Chunk metadata | `chunk_index`, UNIQUE(attachment_id, chunk_index) |
| `message_reactions` | Reaction | `reaction_type` ENUM(LIKE/LOVE/HAHA/WOW/SAD/ANGRY), UNIQUE(message_id, user_id) |
| `message_reads` | Đã đọc | `read_at`, UNIQUE(message_id, user_id) |
| `notifications` | Hàng đợi thông báo | `is_read`, `created_at` |
| `typing_status` | Đang gõ | PRIMARY KEY(conversation_id, user_id) |
| `call_sessions` | Lịch sử gọi | `call_type` ENUM(VOICE/VIDEO), `status` ENUM |
| `call_participants` | Người tham gia gọi | `joined_at`, `left_at` |

### 8.3 Index Hiệu Năng

```sql
CREATE INDEX idx_msg_conv_time ON messages(conversation_id, created_at);
CREATE INDEX idx_msg_sender    ON messages(sender_id);
CREATE INDEX idx_conv_member_user ON conversation_members(user_id);
```

---

## 9. Các Thành Phần Server

### ChatServer.java

Entry point server. Khởi động 3 service:
- TCP Server trên port **9000** — `ClientHandler` (một thread per connection)
- WebSocket Server trên port **9080** — `WsChatServer`
- UDP MediaRelay trên port **9001** (voice) và **9002** (video)

Duy trì `Map<Long, ClientHandler> onlineClients` — tra cứu nhanh người dùng online theo userId.

### ClientHandler.java / WsChatServer.java

Hai file này xử lý **cùng protocol JSON** nhưng trên hai transport khác nhau. Khi thêm tính năng mới phải cập nhật **cả hai**.

Dispatch theo field `type` trong JSON:
```java
switch (type) {
    case Protocol.TYPE_MESSAGE -> handleDirectMessage(data);
    case Protocol.TYPE_LOGIN   -> handleLogin(data);
    // ...
}
```

### DatabaseManager.java

- Pool HikariCP: max 20, min 3 connection
- Tự init schema từ `sql/schema.sql` khi startup (lọc bỏ DROP/CREATE DATABASE/USE)
- Migration: dùng `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` để không break schema hiện có

### Service Classes

| Service | Trách Nhiệm |
|---------|-------------|
| `AuthService` | Đăng ký (BCrypt hash), đăng nhập, update profile, friend request, presence |
| `ConversationService` | Tìm/tạo private conversation (lazy creation), group conversation, lấy danh sách có preview |
| `GroupService` | Tạo nhóm, thêm/xóa/promote/demote member, lưu group message, lấy group info |
| `MessageService` | Lưu message, lấy history (pagination theo `beforeId`), unsend, delivery tracking |
| `FileService` | Upload chunked, reassembly, download, xóa file vật lý |

### MediaRelayServer.java

TURN-like relay đơn giản. Mỗi cuộc gọi có một "session" gồm 2 slot (A và B). Relay bắt đầu khi cả hai peer đã đăng ký. Không store traffic — chỉ forward UDP packet.

---

## 10. Các Thành Phần Client

### ChatClient.java

`Application` JavaFX — entry point. Quản lý:
- Lựa chọn transport (WebSocket vs TCP)
- Chuyển scene (Login → Register → Main)
- Route `handleServerMessage()` → `MainView.onServerMessage()`
- Lifecycle kết nối (reconnect, logout)

### RealtimeClient.java

WebSocket client dùng `java.net.http.WebSocket`. Tính năng:
- Auto-reconnect: thử ngrok URL trước, fallback về localhost
- Buffer JSON không đầy đủ (message có thể đến từng phần)
- Listener pattern — callback khi nhận message
- Header `ngrok-skip-browser-warning` khi dùng ngrok

### MainView.java

Layout 4 cột kiểu Zalo:
- **NavRail** (68px) — icon chuyển tab
- **Sidebar** (320px) — danh sách hội thoại / bạn bè / profile
- **Content Area** — vùng chat chính
- **Right Info Panel** (280px) — thông tin người dùng / nhóm

### ChatTab.java (~1888 dòng)

Component chat cốt lõi. Xử lý:
- `ConvItem` — item trong danh sách hội thoại (tên, tin nhắn cuối, thời gian, avatar, badge unread)
- Custom `ListCell` cho conversation list
- Render message bubble: tin của mình (phải, xanh) / tin của người khác (trái, xám)
- Loại nội dung: Text, Sticker, Image (click phóng to), File
- Reply quote, emoji picker, context menu, group info popup

### Call Handlers

**VoiceCallHandler**: Capture PCM 16kHz mono → noise gate (RMS threshold 0.008) → AGC → jitter buffer 60ms → UDP relay.

**VideoCallHandler**: Sarxos webcam → resize 320×240 → encode JPEG → UDP → decode và render phía nhận. Hỗ trợ local PiP và đổi camera mid-call.

---

## 11. Tính Năng Hiện Có

### Hoàn Thành ✅

- Đăng ký / Đăng nhập với BCrypt
- Dark / Light theme toggle
- Chat 1-1: text, emoji, sticker, reply, unsend, 6 reaction, typing indicator, status tick
- Chat nhóm: tạo, thêm/xóa member, promote/demote admin, đổi tên, mô tả, info popup, rời nhóm
- Gửi hình (click phóng to)
- Gửi file (chunked transfer)
- Hệ thống bạn bè: tìm kiếm, gửi/chấp nhận/từ chối lời mời, danh sách theo alphabet
- Gọi voice: PCM 16kHz, noise gate, AGC, jitter buffer, UDP relay
- Gọi video: webcam Sarxos, JPEG + UDP relay, remote + local PiP, đổi camera
- Desktop notification
- Danh sách hội thoại với preview tin nhắn cuối
- Online/offline presence
- Cache tin nhắn local (JSON file trong `chat_history/`)
- Persistence tin nhắn trên server (MySQL)

### Chưa Làm / Còn Thiếu ❌

Xem chi tiết trong [MISSING_FEATURES.md](MISSING_FEATURES.md). Tóm tắt:

| Nhóm | Số Tính Năng Thiếu |
|------|--------------------|
| Nhắn tin | 11 |
| Cuộc gọi | 4 |
| Kết bạn & Danh bạ | 3 |
| Giao diện & UX | 7 |
| Bảo mật | 7 vấn đề |
| Kiến trúc & QoL | 4 vấn đề |

---

## 12. Hướng Dẫn Build & Chạy

### Yêu Cầu

- Java 17+ (kiểm tra: `java -version`)
- MySQL 8.x chạy trên `127.0.0.1:3306`
- Maven 3.8+ (kiểm tra: `mvn -version`)

### Build (đóng gói fat JAR)

```bash
mvn clean package -DskipTests
```

### Chạy Server

```bash
# Cách 1 — Maven exec (khuyến nghị khi dev)
mvn compile exec:java

# Cách 2 — chỉ định class
mvn exec:java -Dexec.mainClass="com.messenger.server.ChatServer"
```

Server tự tạo database `gui_chat` và khởi tạo schema nếu chưa tồn tại.

### Chạy Client

```bash
mvn javafx:run
```

> **Không dùng** `java -jar target/messenger-app-1.0.0-jar-with-dependencies.jar` — JavaFX sẽ không nhận module path đúng.

---

## 13. Cấu Hình

### app.properties (`src/main/resources/app.properties`)

```properties
# Local (mặc định)
server.baseUrl=http://localhost:9080

# Ngrok (khi muốn kết nối từ xa)
server.baseUrl=https://your-ngrok-url.ngrok-free.dev
```

Client đọc file này qua `AppConfig.java` để xác định URL WebSocket và base HTTP.

### Ports

| Port | Service | Protocol |
|------|---------|----------|
| 9000 | ChatServer (TCP) | JSON-over-TCP (legacy) |
| 9001 | MediaRelayServer (Voice) | UDP PCM audio |
| 9002 | MediaRelayServer (Video) | UDP JPEG frames |
| 9080 | WsChatServer (WebSocket) | JSON-over-WebSocket |
| 3306 | MySQL | JDBC |

### Cấu Hình MySQL

DatabaseManager dùng hardcode credentials mặc định:
- Host: `127.0.0.1:3306`
- Database: `gui_chat` (tự tạo)
- User: `root`, Password: `` (rỗng)

> ⚠️ **Bảo mật**: Đây là cấu hình dev/demo. Không deploy lên production với credentials này.

### Logging (`logback.xml`)

Log ra console và file `logs/messenger*.log`:
- Rolling daily, giữ 30 ngày
- Giới hạn 500MB/file
- Cấp độ mặc định: `INFO`

---

## 14. Troubleshooting

### Lỗi JavaFX không tìm thấy

```
Error: JavaFX runtime components are missing
```
**Giải pháp**: Luôn dùng `mvn javafx:run`. Không dùng `java -jar`.

### MySQL Connection Refused

```
HikariPool: Failed to validate connection
```
**Giải pháp**:
1. Đảm bảo MySQL đang chạy: `mysql -u root -e "SELECT 1"`
2. Kiểm tra port 3306 không bị chiếm
3. Database `gui_chat` sẽ tự tạo — không cần tạo thủ công

### WebSocket kết nối thất bại

```
RealtimeClient: Connection failed, trying fallback
```
**Giải pháp**:
1. Xác nhận server đã khởi động và lắng nghe port 9080
2. Kiểm tra `app.properties` đúng URL
3. Client tự fallback sang localhost nếu ngrok thất bại

### Webcam không nhận diện (Video Call)

Sarxos webcam-capture 0.3.12 có thể không tương thích một số webcam trên JDK 17+. Kiểm tra log khi bắt đầu video call.

### Xem Logs

```bash
# Log file (rolling)
tail -f logs/messenger.log

# Hoặc xem console khi chạy mvn
```

---

*Cập nhật lần cuối: 2026-06-19*
