# Moji Messenger — Desktop Chat Application

> Messenger / Zalo clone — Java 17 + JavaFX + MySQL  
> Version 1.0.0 — Last updated: 2026-06-19

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Architecture Overview](#architecture-overview)
3. [Directory Structure](#directory-structure)
4. [Server Components](#server-components)
5. [Client Components](#client-components)
6. [Shared Protocol & Models](#shared-protocol--models)
7. [Database Schema](#database-schema)
8. [Transport & Data Flow](#transport--data-flow)
9. [Features](#features)
10. [Known Issues](#known-issues)
11. [Build & Run](#build--run)
12. [Troubleshooting](#troubleshooting)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Core | Java 17, JavaFX 17.0.6, Maven |
| Server | WebSocket (java-websocket 1.5.3), TCP Socket, UDP relay |
| Database | MySQL 8.x, HikariCP 5.0.1, jBCrypt 0.4 |
| Client | JavaFX Controls/Graphics/Web, Gson 2.10.1 |
| Call (Voice) | Java Sound API (PCM 16kHz), UDP relay |
| Call (Video) | Sarxos webcam-capture 0.3.12, JPEG + UDP relay |
| Logging | SLF4J 2.0.7 + Logback 1.4.7 |
| Build | Maven assembly plugin (fat JAR) |

---

## Architecture Overview

```
+-------------+     WebSocket/9080     +--------------+     SQL      +---------+
|  Client A   | <---------------------> |  ChatServer  | <---------> |  MySQL  |
| (JavaFX UI) |                        | (TCP + WS)   |             |   8.x   |
+------+------+                        +------+-------+             +---------+
       | UDP/9001 (voice)                     |
       | UDP/9002 (video)                     |
       +----------------> MediaRelayServer <--+
                         (UDP relay / NAT hole-punch)
```

Three layers in the codebase:

```
client.ui.* (Presentation)
    + JSON
client.net.* (Transport)
    + WebSocket / TCP
server.service.* (Business Logic)
    + JDBC
server.db.* (Data Access / MySQL)
```

---

## Directory Structure

```
src/main/java/com/messenger/
+-- client/
|   +-- call/
|   |   +-- VoiceCallHandler.java       # PCM capture + UDP + noise gate, AGC, jitter buffer
|   |   +-- VideoCallHandler.java       # Sarxos webcam + JPEG + UDP relay render
|   |   +-- HelperProcessManager.java   # Legacy WebRTC helper (unused)
|   +-- config/
|   |   +-- AppConfig.java              # app.properties reader
|   +-- net/
|   |   +-- RealtimeClient.java         # WebSocket transport (ngrok / production)
|   |   +-- ApiClient.java              # REST API client
|   +-- ui/
|   |   +-- LoginView.java              # Login screen
|   |   +-- RegisterView.java           # Register screen
|   |   +-- MainView.java               # 4-column layout: nav + sidebar + chat + info
|   |   +-- NotificationManager.java    # Desktop notifications
|   |   +-- IconManager.java            # Icon utilities
|   |   +-- StickerManager.java         # Sticker encode/decode
|   |   +-- ThemeManager.java           # CSS theme toggle
|   |   +-- tabs/
|   |       +-- ChatTab.java            # Tab chat: conversation list + message area
|   |       +-- ContactsTab.java        # Tab contacts + friend requests
|   |       +-- ProfileTab.java         # Tab user profile
|   +-- ChatClient.java                 # JavaFX Application entry point
|   +-- ChatHistoryManager.java         # Local message cache (JSON files)
|   +-- ServerConnection.java           # Legacy TCP transport
|
+-- server/
|   +-- db/
|   |   +-- DatabaseManager.java        # HikariCP pool + schema init
|   +-- service/
|   |   +-- AuthService.java            # Register, Login, Friend mgmt, Search
|   |   +-- ConversationService.java    # Conversation CRUD (private + group)
|   |   +-- GroupService.java           # Group CRUD + group messages
|   |   +-- MessageService.java         # Message persistence + history + delivery
|   |   +-- FileService.java            # File upload (chunked) + download
|   +-- ChatServer.java                 # Main server (TCP + WebSocket + MediaRelay)
|   +-- ClientHandler.java              # TCP client handler (JSON protocol)
|   +-- WsChatServer.java               # WebSocket server mirror (JSON protocol)
|   +-- MediaRelayServer.java           # UDP relay (TURN-like) for voice/video
|
+-- shared/
|   +-- model/
|   |   +-- Message.java                # Message entity
|   |   +-- User.java                   # User entity
|   |   +-- Group.java                  # Group entity + GroupMember inner class
|   |   +-- FriendRequest.java          # Friend request entity
|   +-- util/
|   |   +-- BCryptUtil.java             # Password hashing wrapper
|   |   +-- FileTransferUtil.java       # Chunked file transfer helpers
|   +-- Protocol.java                   # All message types, codes, constants
|
+-- test/

src/main/resources/
+-- sql/schema.sql                      # 17 tables DDL
+-- css/dark-theme.css                  # Dark mode styles
+-- css/light-theme.css                 # Light mode styles
+-- app.properties                      # Server URL config (ngrok / local)
+-- logback.xml                         # Logging config
```

---

## Server Components

### ChatServer.java

Entry point. Starts 3 services:
- TCP Server (port 9000)
- WebSocket Server (port 9080)
- MediaRelayServer (UDP ports 9001, 9002)

Manages Map<Long, ClientHandler> onlineClients.

### ClientHandler.java

TCP client handler. Parses JSON lines, dispatches by type field. ~40 message types:

- Auth: REGISTER, LOGIN, LOGOUT, UPDATE_PROFILE
- Messages: MESSAGE, GROUP_MESSAGE, GET_MESSAGE_HISTORY, TYPING, MESSAGE_STATUS, UNSEND, REACTION
- Friends: FRIEND_REQUEST/RESPONSE, FRIEND_LIST, SEARCH_USER, GET_FRIEND_REQUESTS
- Groups: GROUP_CREATE/JOIN/LEAVE/LIST/INFO/UPDATE, GROUP_REMOVE_MEMBER, PROMOTE/DEMOTE
- Conversations: CONVERSATION_LIST, CONVERSATION_DELETE
- Files: FILE_TRANSFER, FILE_CHUNK
- Calls: VOICE/VIDEO_CALL_START/ACCEPT/REJECT/END
- WebRTC: SDP_OFFER/ANSWER, ICE_CANDIDATE, RELAY_INFO

### WsChatServer.java

WebSocket mirror of ClientHandler. Same JSON protocol. Auto-fallback ngrok env local.

### DatabaseManager.java

- HikariCP connection pool (max 20, min 3)
- Schema init from sql/schema.sql (skips DROP/CREATE/USE on startup)
- Migration support via ALTER TABLE IF NOT EXISTS

### Service Classes

**AuthService** — Register (BCrypt), Login, profile update, friend request send/accept/reject, friend list, search users, presence tracking.

**ConversationService** — Find/create private conversations (lazy creation on first message), group conversations, member management, getUserConversations with last message preview.

**GroupService** — Create groups, add/remove members, promote/demote, save group messages, get group info.

**MessageService** — Save messages, load history (pagination via beforeId), unsend, update delivery, markDeliveredForUser (SENT -> DELIVERED on login).

**FileService** — Chunked file upload, reassembly, download, delete.

### MediaRelayServer.java

Simple TURN-like UDP relay on ports 9001 (voice) and 9002 (video). Two-slot sessions, relay starts when both peers registered.

---

## Client Components

### ChatClient.java

JavaFX Application. Manages transport selection (WebSocket vs TCP), scene switching, message routing (handleServerMessage -> MainView.onServerMessage), connection lifecycle.

### RealtimeClient.java

WebSocket client via java.net.http.WebSocket. Features: auto-reconnect fallback (ngrok -> local), JSON parsing with buffering, listener pattern, ngrok header support.

### MainView.java

4-column Zalo-inspired layout: NavRail (68px) + Sidebar (320px) + Content Area + Right Info Panel (280px). Handles tab switching, call UI, message context menu, theme toggle, notifications, global search.

### ChatTab.java (~1888 lines)

Core chat component:
- ConvItem: conversation list item (name, lastMsg, time, avatar, online, unread)
- Conversation list with custom cell rendering
- Message area with bubble rendering:
  - Mine (right, blue) / Theirs (left, gray)
  - Text, Sticker, Image (ImageView with click-to-enlarge), File
  - Reply quote, time, status ticks
- Reply, Emoji picker, Group creation, Group info popup, Image viewer

### ContactsTab.java

Friend requests (accept/reject) + alphabetically sorted friend list.

### Call Handlers

**VoiceCallHandler**: PCM 16kHz mono capture/playback, noise gate (RMS 0.008), AGC, jitter buffer (60ms), UDP relay.

**VideoCallHandler**: Sarxos webcam capture -> 320x240 -> JPEG -> UDP relay. Remote render + local PiP. Camera switching, toggle mid-call.

---

## Shared Protocol & Models

### Protocol.java

38 message types, response codes (200/400/401/404/409/410/411), message statuses (SENT/DELIVERED/SEEN), content types (TEXT/IMAGE/FILE/VIDEO/VOICE/STICKER), reactions (6 emoji), roles (ADMIN/MEMBER).

### Message Format

Request: `{"type": "MESSAGE", "data": {...}}`
Response: `{"type": "SUCCESS", "code": 200, "data": {...}}`

### Models

- Message: id, conversationId, senderId, content, messageType, status, unsent, timestamps + transient fields
- User: id, username, passwordHash, displayName, email, avatarUrl, presence
- Group: id, name, creatorId, members (GroupMember with userId + role)
- FriendRequest: id, senderId, receiverId, status

---

## Database Schema

17 tables in database gui_chat:

| Table | Purpose | Key Columns |
|---|---|---|
| users | Accounts | id, username, password_hash, display_name, avatar_url, presence, last_seen |
| user_settings | Preferences | theme, language_code, notification_enabled |
| login_history | Audit | ip_address, device_info |
| friend_requests | Request queue | sender_id, receiver_id, status |
| friends | Pairs | user1_id, user2_id |
| blocks | Block list | blocker_id, blocked_id |
| conversations | Chat rooms | type (PRIVATE/GROUP), title, owner_id, description |
| conversation_members | Members | user_id, role (ADMIN/MEMBER) |
| messages | All messages | conversation_id, sender_id, content, message_type, status, is_unsent, reply_to_id |
| attachments | Files | file_name, file_path, mime_type, file_size |
| file_chunks | Chunk metadata | chunk_index, chunk_path |
| message_reactions | Reactions | reaction_type (6 types) |
| message_reads | Read receipts | read_at |
| notifications | Queue | title, content, is_read |
| typing_status | Typing | is_typing, updated_at |
| call_sessions | Call history | call_type, status, started_at, ended_at |
| call_participants | Participants | joined_at, left_at |

---

## Transport & Data Flow

### Connection

```
Client -> read app.properties -> HTTP? -> WebSocket (wss) : TCP
    -> Login -> SUCCESS -> Request friends, conversations, groups
```

### Private Message

```
A send() -> "MESSAGE" + receiverId
Server handleDirectMessage -> saveMessage(DB) -> forward if B online -> status back
```

### Group Message

```
A send() -> "GROUP_MESSAGE" + groupId
Server handleGroupMessage -> saveGroupMessage(DB) -> forward to all online members
```

### Video Call

```
A prepareForCall() (UDP socket)
A -> VIDEO_CALL_START -> server -> B
B accept -> VIDEO_CALL_ACCEPT -> server creates session
Server -> RELAY_INFO (sessionId + ports) -> A + B
A capture webcam -> JPEG -> UDP relay -> B
B capture -> JPEG -> UDP relay -> A
```

---

## Features

### Done

- Register/Login with BCrypt
- Dark/Light theme toggle
- 1-1 chat: text, emoji, stickers, reply, unsend, reactions (6), typing indicator, status ticks
- Group chat: create, add/remove members, promote/demote admin, rename, description, info popup, leave
- Image send/render with click-to-enlarge viewer
- File send with chunked transfer
- Friend system: search, request, accept/reject, alphabetical list
- Voice call: PCM 16kHz, noise gate, AGC, jitter buffer, UDP relay
- Video call: Sarxos webcam, JPEG + UDP relay, remote + local PiP, camera toggle
- Desktop notifications
- Conversation list with last message preview
- Online/offline presence
- Local message cache (JSON files in chat_history/)
- Server message persistence (MySQL)

### Missing

- Multi-device login
- End-to-end encryption
- Forward message
- Search within conversation
- Block user (DB exists, no code)
- Unfriend (no code)
- Call history (DB exists, no code)
- Voice message (DB type exists, no capture UI)
- Link preview
- @Mention
- Pin conversation server-side
- Mute conversation
- Sound on new message
- Scroll-to-bottom button

---

## Build & Run

### Prerequisites

- Java 17+
- MySQL 8.x running on 127.0.0.1:3306
- Maven 3.8+

### Build

```bash
mvn clean package -DskipTests
```

### Run Server

```bash
mvn compile exec:java
```

Or: `mvn exec:java -Dexec.mainClass="com.messenger.server.ChatServer"`

### Run Client

```bash
mvn javafx:run
```

Cannot use `java -jar` (JavaFX module requirement).

### Config

Edit `src/main/resources/app.properties`:
- Local: `server.baseUrl=http://localhost:9080`
- Ngrok: `server.baseUrl=https://your-ngrok-url.ngrok-free.dev`

### Ports

| Port | Service | Protocol |
|---|---|---|
| 9000 | ChatServer (TCP) | Legacy JSON-over-TCP |
| 9001 | MediaRelay (Voice) | UDP PCM audio |
| 9002 | MediaRelay (Video) | UDP JPEG frames |
| 9080 | ChatServer (WebSocket) | JSON-over-WS |
| 3306 | MySQL | JDBC |

---

## Troubleshooting

### JavaFX missing error

Always use `mvn javafx:run` for client. Do not use `java -jar`.

### MySQL connection refused

Ensure MySQL is running on 127.0.0.1:3306. Database gui_chat is auto-created.

### WebSocket failure

Check server is running, check app.properties URL. Client auto-falls back from ngrok to local.

### Logs

Files in logs/messenger*.log (rolling, 30 days, 500MB cap). Console + file configured in logback.xml.
