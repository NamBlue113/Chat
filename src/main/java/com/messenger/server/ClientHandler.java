package com.messenger.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.messenger.server.service.AuthService;
import com.messenger.server.service.ConversationService;
import com.messenger.server.service.FileService;
import com.messenger.server.service.GroupService;
import com.messenger.server.service.MessageService;
import com.messenger.shared.Protocol;
import com.messenger.shared.model.Message;
import com.messenger.shared.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private static final Gson gson = new Gson();

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected;
    private long userId = -1;
    private String username = "";

    // File transfer state
    private final Map<String, FileReceiver> pendingFiles = new ConcurrentHashMap<>();

    private static class FileReceiver {
        String fileId;
        String fileName;
        String filePath;
        int totalChunks;
        int receivedChunks;
        long fileSize;
        long bytesReceived;

        FileReceiver(String fileId, String fileName, int totalChunks, long fileSize) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.filePath = "received_files" + File.separator + fileId + "_" + fileName;
            this.totalChunks = totalChunks;
            this.fileSize = fileSize;
            File dir = new File("received_files");
            if (!dir.exists()) dir.mkdirs();
        }

        boolean isComplete() { return receivedChunks >= totalChunks; }
    }

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.connected = true;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            String line;
            while (connected && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    JsonObject request = gson.fromJson(line, JsonObject.class);
                    handleMessage(request);
                } catch (Exception e) {
                    logger.error("Failed to parse message: {}", e.getMessage());
                    sendError(Protocol.CODE_ERROR, "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            if (connected) {
                logger.error("Connection error for user {}: {}", userId, e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void handleMessage(JsonObject request) {
        String type = request.has("type") ? request.get("type").getAsString() : "";
        JsonObject data = request.has("data") ? request.getAsJsonObject("data") : new JsonObject();

        logger.debug("Received: type={} from userId={}", type, userId);

        switch (type) {
            case Protocol.TYPE_REGISTER:
                handleRegister(data);
                break;
            case Protocol.TYPE_LOGIN:
                handleLogin(data);
                break;
            case Protocol.TYPE_LOGOUT:
                handleLogout();
                break;
            case Protocol.TYPE_UPDATE_PROFILE:
                handleUpdateProfile(data);
                break;
            case Protocol.TYPE_SEARCH_USER:
                handleSearchUser(data);
                break;
            case Protocol.TYPE_MESSAGE:
                handleDirectMessage(data);
                break;
            case Protocol.TYPE_GET_MESSAGE_HISTORY:
                handleGetMessageHistory(data);
                break;
            case Protocol.TYPE_TYPING:
                handleTyping(data);
                break;
            case Protocol.TYPE_MESSAGE_STATUS:
                handleMessageStatus(data);
                break;
            case Protocol.TYPE_UNSEND:
                handleUnsend(data);
                break;
            case Protocol.TYPE_REACTION:
                handleReaction(data);
                break;
            case Protocol.TYPE_FRIEND_REQUEST:
                handleFriendRequest(data);
                break;
            case Protocol.TYPE_FRIEND_RESPONSE:
                handleFriendResponse(data);
                break;
            case Protocol.TYPE_GET_FRIEND_REQUESTS:
                handleGetFriendRequests();
                break;
            case Protocol.TYPE_FRIEND_LIST:
                handleFriendList();
                break;
            case Protocol.TYPE_GROUP_CREATE:
                handleGroupCreate(data);
                break;
            case Protocol.TYPE_GROUP_MESSAGE:
                handleGroupMessage(data);
                break;
            case Protocol.TYPE_GROUP_JOIN:
                handleGroupJoin(data);
                break;
            case Protocol.TYPE_GROUP_LEAVE:
                handleGroupLeave(data);
                break;
            case Protocol.TYPE_GROUP_LIST:
                handleGroupList();
                break;
            case Protocol.TYPE_GROUP_INFO:
                handleGroupInfo(data);
                break;
            case Protocol.TYPE_GROUP_UPDATE:
                handleGroupUpdate(data);
                break;
            case Protocol.TYPE_GROUP_REMOVE_MEMBER:
                handleGroupRemoveMember(data);
                break;
            case Protocol.TYPE_GROUP_PROMOTE:
                handleGroupPromote(data);
                break;
            case Protocol.TYPE_GROUP_DEMOTE:
                handleGroupDemote(data);
                break;
            case Protocol.TYPE_FILE_TRANSFER:
                handleFileTransfer(data);
                break;
            case Protocol.TYPE_FILE_CHUNK:
                handleFileChunk(data);
                break;
            case Protocol.TYPE_VOICE_CALL_START:
            case Protocol.TYPE_VOICE_CALL_ACCEPT:
            case Protocol.TYPE_VOICE_CALL_REJECT:
            case Protocol.TYPE_VOICE_CALL_END:
            case Protocol.TYPE_VIDEO_CALL_START:
            case Protocol.TYPE_VIDEO_CALL_ACCEPT:
            case Protocol.TYPE_VIDEO_CALL_REJECT:
            case Protocol.TYPE_VIDEO_CALL_END:
                handleCallSignal(type, data);
                break;
            case Protocol.TYPE_CONVERSATION_DELETE:
                handleConversationDelete(data);
                break;
            case Protocol.TYPE_VIDEO_FRAME:
                handleVideoFrame(data);
                break;
            default:
                logger.warn("Unknown message type: {}", type);
                sendError(Protocol.CODE_ERROR, "Unknown message type: " + type);
        }
    }

    private void handleConversationDelete(JsonObject data) {
        if (userId <= 0) return;
        long convId = data.has("conversationId") ? data.get("conversationId").getAsLong() : -1;
        if (convId <= 0) { sendError(Protocol.CODE_ERROR, "conversationId required"); return; }
        boolean success = ConversationService.hideConversation(convId, userId);
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", 200);
        if (success) send(response);
        else sendError(Protocol.CODE_ERROR, "Failed to delete conversation");
    }

    private void handleVideoFrame(JsonObject data) {
        if (userId <= 0) return;
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        String frame = data.has("frame") ? data.get("frame").getAsString() : "";
        if (receiverId <= 0 || frame.isEmpty()) return;
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_VIDEO_FRAME);
            forward.addProperty("senderId", userId);
            forward.addProperty("frame", frame);
            forward.addProperty("seq", data.has("seq") ? data.get("seq").getAsLong() : 0);
            receiver.send(forward);
        }
    }

    // ==================== Authentication Handlers ====================

    private void handleRegister(JsonObject data) {
        String username = data.has("username") ? data.get("username").getAsString() : "";
        String password = data.has("password") ? data.get("password").getAsString() : "";
        String displayName = data.has("displayName") ? data.get("displayName").getAsString() : username;

        if (username.isEmpty() || password.isEmpty()) {
            sendError(Protocol.CODE_ERROR, "Username and password are required");
            return;
        }

        try {
            AuthService.RegisterResult result = AuthService.register(username, password, displayName);
            if (result.isSuccess()) {
                JsonObject response = new JsonObject();
                response.addProperty("type", Protocol.TYPE_SUCCESS);
                response.addProperty("code", Protocol.CODE_OK);
                response.add("data", gson.toJsonTree(result.getUser()));
                send(response);
                logger.info("User registered: {}", username);
            } else {
                sendError(Protocol.CODE_USER_EXISTS, result.getMessage());
            }
        } catch (Exception e) {
            logger.error("Register error", e);
            sendError(Protocol.CODE_ERROR, "Registration failed: " + e.getMessage());
        }
    }

    private void handleLogin(JsonObject data) {
        String username = data.has("username") ? data.get("username").getAsString() : "";
        String password = data.has("password") ? data.get("password").getAsString() : "";

        if (username.isEmpty() || password.isEmpty()) {
            sendError(Protocol.CODE_ERROR, "Username and password are required");
            return;
        }

        try {
            AuthService.LoginResult result = AuthService.login(username, password);
            if (result.isSuccess()) {
                this.userId = result.getUser().getId();
                this.username = result.getUser().getUsername(); result.getUser().setPresence(Protocol.PRESENCE_ONLINE);
                server.registerClient(userId, this);
                AuthService.updatePresence(userId, Protocol.PRESENCE_ONLINE);
                broadcastPresence(userId, Protocol.PRESENCE_ONLINE);

                JsonObject response = new JsonObject();
                response.addProperty("type", Protocol.TYPE_SUCCESS);
                response.addProperty("code", Protocol.CODE_OK);
                response.add("data", gson.toJsonTree(result.getUser()));
                send(response);
                logger.info("User logged in: {} (id={})", username, userId);
            } else {
                sendError(Protocol.CODE_UNAUTHORIZED, result.getMessage());
            }
        } catch (Exception e) {
            logger.error("Login error", e);
            sendError(Protocol.CODE_ERROR, "Login failed: " + e.getMessage());
        }
    }

    private void handleLogout() {
        if (userId > 0) {
            AuthService.updatePresence(userId, Protocol.PRESENCE_OFFLINE);
            broadcastPresence(userId, Protocol.PRESENCE_OFFLINE);
            server.removeClient(userId);
            logger.info("User logged out: {} (id={})", username, userId);
        }
        userId = -1;
        username = "";
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        send(response);
    }

    // ==================== Message Handlers ====================

    private void handleSearchUser(JsonObject data) {
        String keyword = data.has("keyword") ? data.get("keyword").getAsString() : "";
        if (keyword.isEmpty()) {
            sendError(Protocol.CODE_ERROR, "Search keyword is required");
            return;
        }
        try {
            var results = AuthService.searchUsers(keyword);
            JsonObject dataObj = new JsonObject();
            dataObj.add("results", gson.toJsonTree(results));

            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.add("data", dataObj);
            send(response);
        } catch (Exception e) {
            logger.error("Search error", e);
            sendError(Protocol.CODE_ERROR, "Search failed: " + e.getMessage());
        }
    }

    private void handleDirectMessage(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        String content = data.has("content") ? data.get("content").getAsString() : "";
        String msgType = data.has("messageType") ? data.get("messageType").getAsString() : "TEXT";
        if (receiverId <= 0 || content.isEmpty()) {
            sendError(Protocol.CODE_ERROR, "receiverId and content are required");
            return;
        }
        // Xử lý reply
        long replyToId = data.has("replyToId") ? data.get("replyToId").getAsLong() : -1;
        String replyToContent = data.has("replyToContent") ? data.get("replyToContent").getAsString() : "";
        String replyToSender = data.has("replyToSender") ? data.get("replyToSender").getAsString() : "";

        // Persist message via conversation
        long convId = ConversationService.getOrCreatePrivateConversation(userId, receiverId);
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setSenderId(userId);
        msg.setContent(content);
        msg.setMessageType(msgType);
        long msgId = MessageService.saveMessage(msg);
        long timestamp = System.currentTimeMillis();

        // Forward to receiver in realtime
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_MESSAGE);
            forward.addProperty("messageId", msgId);
            forward.addProperty("senderId", userId);
            forward.addProperty("senderName", username);
            forward.addProperty("content", content);
            forward.addProperty("messageType", msgType);
            forward.addProperty("timestamp", timestamp);
            if (replyToId > 0) {
                forward.addProperty("replyToId", replyToId);
                forward.addProperty("replyToContent", replyToContent);
                forward.addProperty("replyToSender", replyToSender);
            }
            receiver.send(forward);
        }
        // Send delivery confirmation
        JsonObject status = new JsonObject();
        status.addProperty("type", Protocol.TYPE_MESSAGE_STATUS);
        status.addProperty("messageId", msgId);
        status.addProperty("receiverId", receiverId);
        status.addProperty("status", receiver != null ? Protocol.STATUS_DELIVERED : Protocol.STATUS_SENT);
        send(status);
    }

    private void handleGetMessageHistory(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long convId = data.has("conversationId") ? data.get("conversationId").getAsLong() : -1;
        int limit = data.has("limit") ? data.get("limit").getAsInt() : 50;
        long beforeId = data.has("beforeId") ? data.get("beforeId").getAsLong() : -1;
        if (convId <= 0) {
            // Try to find by friend ID
            long friendId = data.has("friendId") ? data.get("friendId").getAsLong() : -1;
            if (friendId > 0) {
                convId = ConversationService.findPrivateConversation(userId, friendId);
            }
        }
        if (convId <= 0) {
            // Try group ID (groupId == conversationId for groups)
            convId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        }
        if (convId <= 0) {
            sendError(Protocol.CODE_NOT_FOUND, "Conversation not found");
            return;
        }
        var messages = MessageService.getMessages(convId, limit, beforeId);
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        JsonArray arr = new JsonArray();
        for (var m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.getId());
            o.addProperty("senderId", m.getSenderId());
            o.addProperty("senderName", m.getSenderDisplayName());
            o.addProperty("content", m.getContent());
            o.addProperty("messageType", m.getMessageType());
            o.addProperty("status", m.getStatus());
            o.addProperty("timestamp", m.getCreatedAt().getTime());
            arr.add(o);
        }
        JsonObject dataObj = new JsonObject();
        dataObj.addProperty("conversationId", convId);
        if (data.has("friendId")) dataObj.addProperty("friendId", data.get("friendId").getAsLong());
        if (data.has("groupId")) dataObj.addProperty("groupId", data.get("groupId").getAsLong());
        dataObj.add("messages", arr);
        response.add("data", dataObj);
        send(response);
    }

    private void handleTyping(JsonObject data) {
        if (userId <= 0) return;
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        boolean typing = data.has("typing") && data.get("typing").getAsBoolean();
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_TYPING);
            forward.addProperty("senderId", userId);
            forward.addProperty("typing", typing);
            receiver.send(forward);
        }
    }

    private void handleMessageStatus(JsonObject data) {
        if (userId <= 0) return;
        long senderId = data.has("senderId") ? data.get("senderId").getAsLong() : -1;
        String status = data.has("status") ? data.get("status").getAsString() : "";
        ClientHandler sender = server.getClientHandler(senderId);
        if (sender != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_MESSAGE_STATUS);
            forward.addProperty("senderId", userId);
            forward.addProperty("status", status);
            sender.send(forward);
        }
    }

    private void handleUnsend(JsonObject data) {
        if (userId <= 0) return;
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        long messageId = data.has("messageId") ? data.get("messageId").getAsLong() : -1;
        MessageService.unsendMessage(messageId);
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_UNSEND);
            forward.addProperty("senderId", userId);
            forward.addProperty("messageId", messageId);
            receiver.send(forward);
        }
    }

    private void handleReaction(JsonObject data) {
        if (userId <= 0) return;
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        long messageId = data.has("messageId") ? data.get("messageId").getAsLong() : -1;
        String reaction = data.has("reaction") ? data.get("reaction").getAsString() : "";
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_REACTION);
            forward.addProperty("senderId", userId);
            forward.addProperty("messageId", messageId);
            forward.addProperty("reaction", reaction);
            receiver.send(forward);
        }
    }

    // ==================== Friend Handlers ====================

    private void handleFriendRequest(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long toUserId = data.has("toUserId") ? data.get("toUserId").getAsLong() : -1;
        if (toUserId <= 0) { sendError(Protocol.CODE_ERROR, "Invalid target user"); return; }
        AuthService.sendFriendRequest(userId, toUserId);
        ClientHandler target = server.getClientHandler(toUserId);
        if (target != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_FRIEND_REQUEST);
            forward.addProperty("fromUserId", userId);
            forward.addProperty("fromUsername", username);
            forward.addProperty("toUserId", toUserId);
            target.send(forward);
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        send(response);
    }

    private void handleFriendResponse(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long fromUserId = data.has("fromUserId") ? data.get("fromUserId").getAsLong() : -1;
        String action = data.has("action") ? data.get("action").getAsString() : "accept";
        if ("accept".equalsIgnoreCase(action)) {
            AuthService.addFriend(userId, fromUserId);
            AuthService.updateFriendRequestStatus(fromUserId, userId, "ACCEPTED");
            logger.info("Friendship created: user {} and user {}", userId, fromUserId);
        } else {
            AuthService.updateFriendRequestStatus(fromUserId, userId, "REJECTED");
        }
        ClientHandler requester = server.getClientHandler(fromUserId);
        if (requester != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_FRIEND_RESPONSE);
            forward.addProperty("fromUserId", userId);
            forward.addProperty("fromUsername", username);
            forward.addProperty("action", action);
            requester.send(forward);
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        send(response);
    }

    private void handleGetFriendRequests() {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        var requests = AuthService.getPendingFriendRequests(userId);
        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        JsonArray arr = new JsonArray();
        for (var fr : requests) {
            JsonObject o = new JsonObject();
            o.addProperty("id", fr.getId());
            o.addProperty("senderId", fr.getSenderId());
            o.addProperty("senderUsername", fr.getSenderUsername());
            o.addProperty("senderDisplayName", fr.getSenderDisplayName());
            o.addProperty("status", fr.getStatus());
            arr.add(o);
        }
        JsonObject dataObj = new JsonObject();
        dataObj.add("requests", arr);
        response.add("data", dataObj);
        send(response);
    }

    private void handleUpdateProfile(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        String displayName = data.has("displayName") ? data.get("displayName").getAsString() : null;
        String avatarUrl = data.has("avatarUrl") ? data.get("avatarUrl").getAsString() : null;
        boolean ok = AuthService.updateProfile(userId, displayName, avatarUrl);
        if (ok) {
            User updated = AuthService.getUserById(userId);
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.addProperty("message", "Profile updated");
            if (updated != null) {
                response.add("data", gson.toJsonTree(updated));
            }
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to update profile");
        }
    }

    private void handleFriendList() {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        var friends = AuthService.getFriends(userId);
        JsonObject dataObj = new JsonObject();
        dataObj.add("friends", gson.toJsonTree(friends));

        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        response.add("data", dataObj);
        send(response);
    }

    // ==================== Group Handlers ====================

    private void handleGroupCreate(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        String groupName = data.has("name") ? data.get("name").getAsString() : "";
        GroupService.GroupResult result = GroupService.createGroup(groupName, userId);
        if (result.isSuccess()) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.add("data", gson.toJsonTree(result.getGroup()));
            send(response);
            logger.info("Group '{}' created by user {}", groupName, userId);
        } else {
            sendError(Protocol.CODE_ERROR, result.getMessage());
        }
    }

    private void handleGroupJoin(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        if (GroupService.addMember(groupId, userId)) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to join group");
        }
    }

    private void handleGroupLeave(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        if (GroupService.removeMember(groupId, userId)) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to leave group");
        }
    }

    private void handleGroupList() {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        var groups = GroupService.getUserGroups(userId);
        JsonObject dataObj = new JsonObject();
        dataObj.add("groups", gson.toJsonTree(groups));

        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        response.add("data", dataObj);
        send(response);
    }

    private void handleGroupMessage(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        String content = data.has("content") ? data.get("content").getAsString() : "";
        String msgType = data.has("messageType") ? data.get("messageType").getAsString() : "TEXT";
        if (groupId <= 0 || content.isEmpty()) {
            sendError(Protocol.CODE_ERROR, "groupId and content are required"); return;
        }

        long msgId = GroupService.saveGroupMessage(userId, groupId, content, msgType);

        // Forward to all online group members
        var members = GroupService.getGroupMembers(groupId);
        JsonObject forward = new JsonObject();
        forward.addProperty("type", Protocol.TYPE_GROUP_MESSAGE);
        forward.addProperty("messageId", msgId);
        forward.addProperty("groupId", groupId);
        forward.addProperty("senderId", userId);
        forward.addProperty("senderName", username);
        forward.addProperty("content", content);
        forward.addProperty("messageType", msgType);
        forward.addProperty("timestamp", System.currentTimeMillis());

        for (var member : members) {
            if (member.getUserId() != userId) {
                ClientHandler memberHandler = server.getClientHandler(member.getUserId());
                if (memberHandler != null) memberHandler.send(forward);
            }
        }
    }

    private void handleGroupInfo(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        if (groupId <= 0) { sendError(Protocol.CODE_ERROR, "groupId required"); return; }
        JsonObject info = GroupService.getGroupInfo(groupId);
        if (info.has("id")) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.add("data", info);
            send(response);
        } else {
            sendError(Protocol.CODE_NOT_FOUND, "Group not found");
        }
    }

    private void handleGroupUpdate(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        if (groupId <= 0) { sendError(Protocol.CODE_ERROR, "groupId required"); return; }
        String name = data.has("name") ? data.get("name").getAsString() : null;
        String avatarUrl = data.has("avatarUrl") ? data.get("avatarUrl").getAsString() : null;
        String description = data.has("description") ? data.get("description").getAsString() : null;
        if (name == null && avatarUrl == null && description == null) {
            sendError(Protocol.CODE_ERROR, "Nothing to update"); return;
        }
        if (GroupService.updateGroup(groupId, name, avatarUrl, description)) {
            // Thông báo cho tất cả thành viên online
            var members = GroupService.getGroupMembers(groupId);
            JsonObject fw = new JsonObject();
            fw.addProperty("type", Protocol.TYPE_GROUP_UPDATE);
            fw.addProperty("groupId", groupId);
            if (name != null) fw.addProperty("name", name);
            if (avatarUrl != null) fw.addProperty("avatarUrl", avatarUrl);
            if (description != null) fw.addProperty("description", description);
            for (var m : members) {
                if (m.getUserId() != userId) {
                    ClientHandler h = server.getClientHandler(m.getUserId());
                    if (h != null) h.send(fw);
                }
            }
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.addProperty("message", "Group updated");
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to update group");
        }
    }

    private void handleGroupRemoveMember(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        long targetUserId = data.has("targetUserId") ? data.get("targetUserId").getAsLong() : -1;
        if (groupId <= 0 || targetUserId <= 0) {
            sendError(Protocol.CODE_ERROR, "groupId and targetUserId required"); return;
        }
        // Chỉ admin mới được xóa thành viên
        var members = GroupService.getGroupMembers(groupId);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == userId && m.isAdmin());
        if (!isAdmin) { sendError(Protocol.CODE_UNAUTHORIZED, "Only admins can remove members"); return; }
        if (GroupService.removeMember(groupId, targetUserId)) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            response.addProperty("message", "Member removed");
            send(response);
            // Thông báo cho người bị xóa
            ClientHandler target = server.getClientHandler(targetUserId);
            if (target != null) {
                JsonObject notice = new JsonObject();
                notice.addProperty("type", Protocol.TYPE_GROUP_LEAVE);
                notice.addProperty("groupId", groupId);
                notice.addProperty("kicked", true);
                target.send(notice);
            }
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to remove member");
        }
    }

    private void handleGroupPromote(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        long targetUserId = data.has("targetUserId") ? data.get("targetUserId").getAsLong() : -1;
        if (groupId <= 0 || targetUserId <= 0) {
            sendError(Protocol.CODE_ERROR, "groupId and targetUserId required"); return;
        }
        var members = GroupService.getGroupMembers(groupId);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == userId && m.isAdmin());
        if (!isAdmin) { sendError(Protocol.CODE_UNAUTHORIZED, "Only admins can promote members"); return; }
        if (GroupService.promoteMember(groupId, targetUserId)) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to promote member");
        }
    }

    private void handleGroupDemote(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long groupId = data.has("groupId") ? data.get("groupId").getAsLong() : -1;
        long targetUserId = data.has("targetUserId") ? data.get("targetUserId").getAsLong() : -1;
        if (groupId <= 0 || targetUserId <= 0) {
            sendError(Protocol.CODE_ERROR, "groupId and targetUserId required"); return;
        }
        var members = GroupService.getGroupMembers(groupId);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == userId && m.isAdmin());
        if (!isAdmin) { sendError(Protocol.CODE_UNAUTHORIZED, "Only admins can demote members"); return; }
        if (GroupService.demoteMember(groupId, targetUserId)) {
            JsonObject response = new JsonObject();
            response.addProperty("type", Protocol.TYPE_SUCCESS);
            response.addProperty("code", Protocol.CODE_OK);
            send(response);
        } else {
            sendError(Protocol.CODE_ERROR, "Failed to demote member");
        }
    }

    // ==================== File Transfer Handlers ====================

    private void handleFileTransfer(JsonObject data) {
        if (userId <= 0) { sendError(Protocol.CODE_UNAUTHORIZED, "Not logged in"); return; }
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        String fileId = data.has("fileId") ? data.get("fileId").getAsString() : "";
        String fileName = data.has("fileName") ? data.get("fileName").getAsString() : "file";
        int totalChunks = data.has("totalChunks") ? data.get("totalChunks").getAsInt() : 1;
        long fileSize = data.has("fileSize") ? data.get("fileSize").getAsLong() : 0;

        // Notify receiver about incoming file
        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            JsonObject notify = new JsonObject();
            notify.addProperty("type", Protocol.TYPE_FILE_TRANSFER);
            notify.addProperty("senderId", userId);
            notify.addProperty("senderName", username);
            notify.addProperty("fileId", fileId);
            notify.addProperty("fileName", fileName);
            notify.addProperty("totalChunks", totalChunks);
            notify.addProperty("fileSize", fileSize);
            receiver.send(notify);

            // Prepare file receiver on server side for store-and-forward
            receiver.pendingFiles.put(fileId,
                    new FileReceiver(fileId, fileName, totalChunks, fileSize));
        }

        JsonObject response = new JsonObject();
        response.addProperty("type", Protocol.TYPE_SUCCESS);
        response.addProperty("code", Protocol.CODE_OK);
        send(response);
    }

    private void handleFileChunk(JsonObject data) {
        if (userId <= 0) return;
        long receiverId = data.has("receiverId") ? data.get("receiverId").getAsLong() : -1;
        String fileId = data.has("fileId") ? data.get("fileId").getAsString() : "";
        int chunkIndex = data.has("chunkIndex") ? data.get("chunkIndex").getAsInt() : 0;
        String base64Data = data.has("data") ? data.get("data").getAsString() : "";

        ClientHandler receiver = server.getClientHandler(receiverId);
        if (receiver != null) {
            // Forward chunk to receiver
            JsonObject forward = new JsonObject();
            forward.addProperty("type", Protocol.TYPE_FILE_CHUNK);
            forward.addProperty("senderId", userId);
            forward.addProperty("fileId", fileId);
            forward.addProperty("chunkIndex", chunkIndex);
            forward.addProperty("data", base64Data);
            receiver.send(forward);

            // Also write chunk to server-side file for offline delivery
            FileReceiver fr = receiver.pendingFiles.get(fileId);
            if (fr != null) {
                try {
                    byte[] chunkBytes = Base64.getDecoder().decode(base64Data);
                    try (FileOutputStream fos = new FileOutputStream(fr.filePath, true)) {
                        fos.write(chunkBytes);
                    }
                    fr.receivedChunks++;
                    fr.bytesReceived += chunkBytes.length;
                    if (fr.isComplete()) {
                        receiver.pendingFiles.remove(fileId);
                        logger.info("File transfer complete: {} ({} bytes)", fr.fileName, fr.fileSize);
                    }
                } catch (IOException e) {
                    logger.error("Failed to write file chunk: {}", e.getMessage());
                }
            }
        }

        JsonObject ack = new JsonObject();
        ack.addProperty("type", Protocol.TYPE_SUCCESS);
        ack.addProperty("code", Protocol.CODE_OK);
        ack.addProperty("fileId", fileId);
        ack.addProperty("chunkIndex", chunkIndex);
        send(ack);
    }

    // ==================== Call Handlers ====================

    private void handleCallSignal(String type, JsonObject data) {
        if (userId <= 0) return;
        long targetId = data.has("targetId") ? data.get("targetId").getAsLong() : -1;
        ClientHandler target = server.getClientHandler(targetId);

        // Khi B chấp nhận cuộc gọi, tạo relay session
        if (type.contains("ACCEPT") && target != null) {
            boolean isVideo = type.contains("VIDEO");
            String sessionId = String.format("%08d", Math.abs((userId * 31 + targetId * 17 + System.currentTimeMillis()) % 100000000));

            // Gửi RELAY_INFO cho A (người gọi)
            JsonObject relayInfoA = new JsonObject();
            relayInfoA.addProperty("type", "RELAY_INFO");
            relayInfoA.addProperty("sessionId", sessionId);
            relayInfoA.addProperty("voicePort", MediaRelayServer.VOICE_RELAY_PORT);
            relayInfoA.addProperty("videoPort", MediaRelayServer.VIDEO_RELAY_PORT);
            relayInfoA.addProperty("mediaTcpPort", MediaRelayServer.MEDIA_TCP_PORT);
            relayInfoA.addProperty("isVideo", isVideo);
            relayInfoA.addProperty("targetId", targetId);
            relayInfoA.addProperty("senderId", userId);
            relayInfoA.addProperty("senderName", username);
            relayInfoA.addProperty("caller", true);
            target.send(relayInfoA);

            // Gửi RELAY_INFO cho B (người nhận/người chấp nhận)
            JsonObject relayInfoB = new JsonObject();
            relayInfoB.addProperty("type", "RELAY_INFO");
            relayInfoB.addProperty("sessionId", sessionId);
            relayInfoB.addProperty("voicePort", MediaRelayServer.VOICE_RELAY_PORT);
            relayInfoB.addProperty("videoPort", MediaRelayServer.VIDEO_RELAY_PORT);
            relayInfoB.addProperty("mediaTcpPort", MediaRelayServer.MEDIA_TCP_PORT);
            relayInfoB.addProperty("isVideo", isVideo);
            relayInfoB.addProperty("targetId", userId);
            relayInfoB.addProperty("senderId", targetId);
            relayInfoB.addProperty("senderName", username);
            relayInfoB.addProperty("caller", false);
            send(relayInfoB);

            logger.info("Relay session {} created for {} call between {} and {}",
                    sessionId, isVideo ? "video" : "voice", userId, targetId);
            return;
        }

        // Kết thúc cuộc gọi: dọn relay session
        if (type.contains("END") || type.contains("REJECT")) {
            if (data.has("sessionId")) {
                String sid = data.get("sessionId").getAsString();
                server.getMediaRelay().unregister(sid);
                if (data.has("isVideo") && data.get("isVideo").getAsBoolean()) {
                    server.getMediaRelay().unregister(sid, true);
                }
            }
        }

        // Forward tín hiệu cơ bản
        if (target != null) {
            JsonObject forward = new JsonObject();
            forward.addProperty("type", type);
            forward.addProperty("senderId", userId);
            forward.addProperty("senderName", username);
            if (data.has("port")) forward.addProperty("port", data.get("port").getAsInt());
            if (data.has("audioPort")) forward.addProperty("audioPort", data.get("audioPort").getAsInt());
            if (data.has("sessionId")) forward.addProperty("sessionId", data.get("sessionId").getAsString());
            if (data.has("isVideo")) forward.addProperty("isVideo", data.get("isVideo").getAsBoolean());
            target.send(forward);
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("type", type.contains("REJECT") ? type : type + "_REJECT");
            response.addProperty("code", Protocol.CODE_USER_OFFLINE);
            response.addProperty("message", "User is offline");
            send(response);
            logger.info("Call target {} is offline", targetId);
        }
    }

    // ==================== Helpers ====================

    public void send(JsonObject message) {
        if (writer != null && connected) {
            try { writer.println(gson.toJson(message)); } catch (Exception e) {
                logger.error("Failed to send to user {}: {}", userId, e.getMessage());
            }
        }
    }

    private void sendError(int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", Protocol.TYPE_ERROR);
        error.addProperty("code", code);
        error.addProperty("message", message);
        send(error);
    }

    private void broadcastPresence(long userId, String presence) {
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", Protocol.TYPE_PRESENCE);
        broadcast.addProperty("userId", userId);
        broadcast.addProperty("presence", presence);
        for (ClientHandler handler : server.getOnlineClients().values()) {
            if (handler != null && handler != this) handler.send(broadcast);
        }
    }

    public void disconnect() {
        connected = false;
        if (userId > 0) {
            AuthService.updatePresence(userId, Protocol.PRESENCE_OFFLINE);
            broadcastPresence(userId, Protocol.PRESENCE_OFFLINE);
            server.removeClient(userId);
            logger.info("User disconnected: {} (id={})", username, userId);
        }
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
    

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
}
