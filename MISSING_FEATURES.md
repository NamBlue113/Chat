# Danh Sách Tính Năng Còn Thiếu So Với Messenger / Zalo

> **Dự án**: Moji Moji Messenger (Java 17 + JavaFX + MySQL)
> **Ngày tạo**: 2026-06-19
> **Mục đích**: Theo dõi các tính năng cần phát triển thêm

---

## 1. Nhắn Tin

### 1.1 Reply / Trả lời tin nhắn
- **Mô tả**: Nhấn giữ/click chuột phải vào tin nhắn → chọn "Trả lời" → hiển thị quote của tin gốc phía trên ô input
- **File liên quan**: `ChatTab.java` (message bubble), `MessageService.java`
- **DB**: Cần thêm `reply_to_id` vào bảng `messages`
- **Ưu tiên**: 🔴 Cao

### 1.2 Forward / Chuyển tiếp tin nhắn
- **Mô tả**: Chọn tin nhắn → Forward → pick conversation → gửi
- **File liên quan**: `ChatTab.java`, `ConversationService.java`
- **DB**: Không cần thay đổi schema
- **Ưu tiên**: 🔴 Cao

### 1.3 Tìm kiếm tin nhắn trong cuộc hội thoại
- **Mô tả**: Ô search trong chat area → tìm kiếm nội dung tin nhắn (không chỉ search user)
- **File liên quan**: `ChatTab.java`, `MessageService.java`, `ClientHandler.java`
- **DB**: Có thể dùng `LIKE` hoặc thêm FULLTEXT index
- **Ưu tiên**: 🟡 Trung bình

### 1.4 Thu hồi tin nhắn (Unsend) — UI
- **Mô tả**: Backend đã có `handleUnsend`, cần thêm nút "Thu hồi" khi click chuột phải vào bubble tin nhắn của mình
- **File liên quan**: `ChatTab.java` (context menu trên bubble)
- **DB**: Đã có `is_unsent` trong `messages`
- **Ghi chú**: ⚠️ Backend đã xong, chỉ thiếu UI trigger
- **Ưu tiên**: 🟡 Trung bình

### 1.5 Hiển thị reaction trên bubble
- **Mô tả**: Khi người dùng thả reaction, hiển thị icon nhỏ dưới góc bubble
- **File liên quan**: `ChatTab.java`, `ClientHandler.java`
- **DB**: Đã có `message_reactions`
- **Ghi chú**: ⚠️ Backend đã xong, chỉ thiếu hiển thị UI
- **Ưu tiên**: 🟡 Trung bình

### 1.6 Seen / Delivered status (tick xanh)
- **Mô tả**: Hiển thị trạng thái "Đã gửi" (1 tick) / "Đã nhận" (2 tick) / "Đã xem" (2 tick xanh) dưới bubble
- **File liên quan**: `ChatTab.java`, `MessageService.java`
- **DB**: Đã có `message_reads` + status tracking
- **Ghi chú**: ⚠️ Backend đã có, thiếu UI hiển thị
- **Ưu tiên**: 🟡 Trung bình

### 1.7 @Mention trong group chat
- **Mô tả**: Gõ `@` → dropdown chọn thành viên nhóm → mention có highlight + notification
- **File liên quan**: `ChatTab.java`, `GroupService.java`
- **DB**: Có thể lưu dạng `@userId` trong content
- **Ưu tiên**: 🟢 Thấp

### 1.8 Link preview
- **Mô tả**: Khi gửi URL, tự động fetch title + description + image
- **File liên quan**: `ChatTab.java`, `MessageService.java`
- **DB**: Có thể thêm bảng `link_previews`
- **Ưu tiên**: 🟢 Thấp

### 1.9 Ghim conversation lên đầu
- **Mô tả**: Pin/unpin conversation (code đã có `pinConv()` nhưng không có UI)
- **File liên quan**: `ChatTab.java`
- **Ghi chú**: ⚠️ Code đã có hàm, thiếu nút trigger
- **Ưu tiên**: 🟢 Thấp

### 1.10 Thu âm giọng nói
- **Mô tả**: Nút mic → ghi âm → gửi dạng voice message
- **File liên quan**: `ChatTab.java`, `FileService.java`
- **DB**: Đã có type `VOICE` trong `messages.message_type`
- **Ưu tiên**: 🟢 Thấp

### 1.11 Tin nhắn tạm thời (Disappearing messages)
- **Mô tả**: Tin nhắn tự hủy sau 1h/24h/7 ngày
- **DB**: Có thể thêm `ttl` column vào `messages`
- **Ưu tiên**: ⚪ Rất thấp

---

## 2. Chat Nhóm ✅ *(Đã hoàn thành)*

### 2.1 Xem danh sách thành viên ✅
- **Mô tả**: Click vào header group → popup hiển thị danh sách member (avatar, tên, role)
- **File liên quan**: `ChatTab.java`, `GroupService.java`, `ClientHandler.java`, `WsChatServer.java`
- **DB**: Đã có `conversation_members`
- **Ghi chú**: Group Info Popup với member list, phân biệt Admin/Member

### 2.2 Thêm / Xóa thành viên ✅
- **Mô tả**: UI thêm bạn bè vào nhóm, xóa member khỏi nhóm
- **File liên quan**: `ChatTab.java`, `GroupService.java`, `ClientHandler.java`, `WsChatServer.java`
- **DB**: Đã có `conversation_members`
- **Ghi chú**: Nút "+ Thêm" → dialog chọn bạn → gửi `GROUP_JOIN`; Admin có nút "Xóa" cho từng member

### 2.3 Phân quyền Admin / Member ✅
- **Mô tả**: Promote/demote admin từ giao diện
- **File liên quan**: `GroupService.java`, `ChatTab.java`
- **DB**: Đã có `role` trong `conversation_members`
- **Ghi chú**: Nút "Lên QTV" / "Hạ" trong Group Info (chỉ admin mới thấy)

### 2.4 Ảnh đại diện nhóm ✅
- **Mô tả**: Avatar cho group chat (hiển thị trong sidebar + header + group info)
- **DB**: Đã có `avatar_url` trong `conversations`
- **Ghi chú**: Hiển thị avatar trong conversation list, header, group info popup

### 2.5 Đổi tên nhóm & Mô tả nhóm ✅
- **Mô tả**: Sửa tên nhóm, thêm description từ context menu hoặc group info
- **DB**: `conversations` đã có `title`, `description` column
- **Ghi chú**: Context menu "Đổi tên" gửi `GROUP_UPDATE` cho group; Group Info có nút "Sửa tên & mô tả"

### 2.6 Rời nhóm ✅
- **Mô tả**: Nút "Rời nhóm" trong context menu và group info popup
- **Ghi chú**: Xử lý cả kicked (bị xóa khỏi nhóm)

---

## 3. Cuộc Gọi Voice / Video

### 3.1 Màn hình cuộc gọi đến (Incoming call UI)
- **Mô tả**: Popup/fullscreen hiển thị khi có cuộc gọi đến (avatar + tên + nút Accept/Reject)
- **File liên quan**: `VoiceCallHandler.java`, `VideoCallHandler.java`, `MainView.java`
- **Ưu tiên**: 🟡 Trung bình

### 3.2 Giao diện cuộc gọi active
- **Mô tả**: Màn hình trong cuộc gọi: camera preview (video), mute/unmute, speaker, end call, timer
- **File liên quan**: `VoiceCallHandler.java`, `VideoCallHandler.java`
- **Ưu tiên**: 🟡 Trung bình

### 3.3 Lịch sử cuộc gọi
- **Mô tả**: Tab/mục hiển thị lịch sử cuộc gọi (đã gọi, đã nhận, nhỡ)
- **DB**: Đã có `call_sessions` + `call_participants`
- **Ưu tiên**: 🟢 Thấp

### 3.4 Chuyển đổi voice ↔ video giữa cuộc gọi
- **Mô tả**: Nút bật/tắt camera khi đang trong cuộc gọi voice
- **Ưu tiên**: 🟢 Thấp

---

## 4. Kết Bạn & Danh Bạ

### 4.1 Chặn người dùng (Block)
- **Mô tả**: Block/unblock user từ profile hoặc context menu
- **DB**: Đã có bảng `blocks`
- **Ghi chú**: ⚠️ DB đã có, không có code backend + UI
- **Ưu tiên**: 🟡 Trung bình

### 4.2 Hủy kết bạn
- **Mô tả**: Unfriend từ danh bạ
- **DB**: Đã có bảng `friends`
- **Ưu tiên**: 🟢 Thấp

### 4.3 Báo cáo / Spam
- **Mô tả**: Report user/message
- **Ưu tiên**: ⚪ Rất thấp

---

## 5. Giao Diện & UX

### 5.1 Xem ảnh phóng to (Image viewer)
- **Mô tả**: Click vào ảnh trong chat → popup fullscreen với zoom, download
- **File liên quan**: `ChatTab.java`
- **Ưu tiên**: 🟡 Trung bình

### 5.2 Notification desktop
- **Mô tả**: Popup notification khi có tin nhắn mới (đang có `NotificationManager` nhưng chưa rõ đã implement)
- **File liên quan**: `NotificationManager.java`
- **Ưu tiên**: 🟡 Trung bình

### 5.3 Sound báo tin nhắn đến
- **Mô tả**: Phát âm thanh khi nhận tin nhắn mới
- **Ưu tiên**: 🟢 Thấp

### 5.4 Loading spinner / Skeleton
- **Mô tả**: Hiệu ứng loading khi tải lịch sử, danh sách bạn bè
- **Ưu tiên**: 🟢 Thấp

### 5.5 Empty state
- **Mô tả**: Màn hình trống khi chưa có cuộc hội thoại / bạn bè / tin nhắn
- **Ưu tiên**: 🟢 Thấp

### 5.6 Nút scroll xuống cuối chat
- **Mô tả**: Khi đang xem tin nhắn cũ, nút ⬇ để về cuối
- **Ưu tiên**: 🟢 Thấp

### 5.7 Biểu tượng ứng dụng (App icon)
- **Mô tả**: Icon cho cửa sổ ứng dụng (có thư mục `icon/` nhưng chưa rõ đã set)
- **File liên quan**: `ChatClient.java`
- **Ưu tiên**: 🟢 Thấp

---

## 6. Bảo Mật

| # | Vấn đề | Mô tả | Ưu tiên |
|---|--------|-------|---------|
| 6.1 | **Không rate limiting** | Login/register có thể brute force | 🔴 Cao |
| 6.2 | **DB root không password** | `ChatServer.java:57` hardcode `"root", ""` | 🔴 Cao |
| 6.3 | **Không HTTPS/WSS** | Toàn bộ traffic plaintext qua ngrok Free | 🔴 Cao |
| 6.4 | **Logging leak thông tin** | Server log chi tiết request | 🟡 Trung bình |
| 6.5 | **Validation password yếu** | Chỉ 4 ký tự tối thiểu | 🟡 Trung bình |
| 6.6 | **Không mã hóa end-to-end** | Message dạng plaintext trong DB | 🟡 Trung bình |
| 6.7 | **Không có session token** | Chỉ dùng userId thuần | 🟡 Trung bình |

---

## 7. Kiến Trúc & Quality of Life

| # | Vấn đề | Mô tả | Ưu tiên |
|---|--------|-------|---------|
| 7.1 | **Không có unit test** | pom.xml có JUnit dep nhưng không thấy test files | 🟡 Trung bình |
| 7.2 | **sarxos.webcam 0.3.12** | Library đã 4+ năm không cập nhật, có thể incompatible JDK 17+ | 🟢 Thấp |
| 7.3 | **TCP và WebSocket song song** | Cần maintain 2 transport cùng lúc | 🟢 Thấp |
| 7.4 | **Duplication giữa ClientHandler + WsChatServer** | Xử lý message gần như giống nhau | 🟢 Thấp |

---

## Tổng Quan

```
Nhắn tin                    11 tính năng thiếu   (2 Cao, 3 Trung bình, 6 Thấp)
Chat nhóm                   0  ✅ Đã hoàn thành
Cuộc gọi                    4  tính năng thiếu   (0 Cao, 2 Trung bình, 2 Thấp)
Kết bạn & Danh bạ           3  tính năng thiếu   (0 Cao, 1 Trung bình, 2 Thấp)
Giao diện & UX              7  tính năng thiếu   (0 Cao, 2 Trung bình, 5 Thấp)
Bảo mật                     7  vấn đề            (3 Cao, 3 Trung bình, 1 Thấp)
Kiến trúc & QoL             4  vấn đề            (0 Cao, 1 Trung bình, 3 Thấp)
────────────────────────────────────────────────────────────────────
Tổng cộng                  36 mục                (5 Cao, 12 Trung bình, 19 Thấp)
```

---
*Cập nhật lần cuối: 2026-06-19*
