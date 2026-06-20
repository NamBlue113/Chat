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
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server that mirrors the TCP ChatServer.
 * Accepts the same JSON protocol over WebSocket frames.
 */
public class WsChatServer extends WebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(WsChatServer.class);
    private static final Gson gson = new Gson();

    private final ChatServer chatServer;
    private final Map<WebSocket, WsSession> sessions = new ConcurrentHashMap<>();

    private static class WsSession {
        long userId = -1;
        String username = "";
        final Map<String, FileReceiver> pendingFiles = new ConcurrentHashMap<>();
    }

    private static class FileReceiver {
        String fileId, fileName, filePath;
        int totalChunks, receivedChunks;
        long fileSize;
        FileReceiver(String fileId, String fileName, int totalChunks, long fileSize) {
            this.fileId = fileId; this.fileName = fileName; this.totalChunks = totalChunks; this.fileSize = fileSize;
            this.filePath = "received_files" + File.separator + fileId + "_" + fileName;
            new File("received_files").mkdirs();
        }
        boolean isComplete() { return receivedChunks >= totalChunks; }
    }

    public WsChatServer(InetSocketAddress address, ChatServer chatServer) {
        super(address);
        this.chatServer = chatServer;
        setConnectionLostTimeout(30);
    }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sessions.put(conn, new WsSession());
        logger.info("WS connected: {}", conn.getRemoteSocketAddress());
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        WsSession s = sessions.remove(conn);
        if (s != null && s.userId > 0) {
            AuthService.updatePresence(s.userId, Protocol.PRESENCE_OFFLINE);
            broadcastPresence(conn, s.userId, Protocol.PRESENCE_OFFLINE);
            chatServer.removeClient(s.userId);
        }
    }

    @Override public void onMessage(WebSocket conn, String text) {
        WsSession s = sessions.get(conn);
        if (s == null) return;
        try {
            String payload = text == null ? "" : text.trim();
            if (payload.isEmpty()) {
                logger.warn("WS ignored empty message from {}", conn.getRemoteSocketAddress());
                return;
            }
            if (!payload.startsWith("{")) {
                logger.warn("WS non-JSON from {}: {}", conn.getRemoteSocketAddress(), preview(payload));
                sendError(conn, Protocol.CODE_ERROR, "Invalid JSON");
                return;
            }
            JsonObject request = gson.fromJson(payload, JsonObject.class);
            if (request == null || !request.has("type")) {
                logger.warn("WS invalid protocol message from {}: {}", conn.getRemoteSocketAddress(), preview(payload));
                sendError(conn, Protocol.CODE_ERROR, "Invalid message format");
                return;
            }
            handleMessage(conn, s, request);
        } catch (Exception e) {
            logger.error("WS parse error from {}: {}; payload={}",
                    conn.getRemoteSocketAddress(), e.getMessage(), preview(text));
            sendError(conn, Protocol.CODE_ERROR, "Invalid JSON");
        }
    }

    @Override public void onError(WebSocket conn, Exception ex) {
        logger.error("WS error: {}", ex.getMessage());
    }

    @Override public void onStart() {
        logger.info("WebSocket server started on port {}", getPort());
    }

    // === Message Routing ===

    private void handleMessage(WebSocket c, WsSession s, JsonObject req) {
        String type = req.has("type") ? req.get("type").getAsString() : "";
        JsonObject d = req.has("data") ? req.getAsJsonObject("data") : new JsonObject();
        switch (type) {
            case Protocol.TYPE_REGISTER:   handleRegister(c,s,d); break;
            case Protocol.TYPE_LOGIN:      handleLogin(c,s,d); break;
            case Protocol.TYPE_LOGOUT:     handleLogout(c,s); break;
            case Protocol.TYPE_UPDATE_PROFILE: handleUpdateProfile(c,s,d); break;
            case Protocol.TYPE_SEARCH_USER:    handleSearchUser(c,s,d); break;
            case Protocol.TYPE_MESSAGE:        handleDirectMessage(c,s,d); break;
            case Protocol.TYPE_GET_MESSAGE_HISTORY: handleGetHistory(c,s,d); break;
            case Protocol.TYPE_TYPING:         handleTyping(c,s,d); break;
            case Protocol.TYPE_UNSEND:         handleUnsend(c,s,d); break;
            case Protocol.TYPE_REACTION:       handleReaction(c,s,d); break;
            case Protocol.TYPE_FRIEND_REQUEST: handleFriendReq(c,s,d); break;
            case Protocol.TYPE_FRIEND_RESPONSE: handleFriendResp(c,s,d); break;
            case Protocol.TYPE_GET_FRIEND_REQUESTS: handleGetFriendReqs(c,s); break;
            case Protocol.TYPE_FRIEND_LIST:    handleFriendList(c,s); break;
            case Protocol.TYPE_GROUP_CREATE:   handleGroupCreate(c,s,d); break;
            case Protocol.TYPE_GROUP_MESSAGE:  handleGroupMsg(c,s,d); break;
            case Protocol.TYPE_GROUP_JOIN:     handleGroupJoin(c,s,d); break;
            case Protocol.TYPE_GROUP_LEAVE:    handleGroupLeave(c,s,d); break;
            case Protocol.TYPE_GROUP_LIST:     handleGroupList(c,s); break;
            case Protocol.TYPE_GROUP_INFO:     handleGroupInfo(c,s,d); break;
            case Protocol.TYPE_GROUP_UPDATE:   handleGroupUpdate(c,s,d); break;
            case Protocol.TYPE_GROUP_REMOVE_MEMBER: handleGroupRemoveMember(c,s,d); break;
            case Protocol.TYPE_GROUP_PROMOTE:  handleGroupPromote(c,s,d); break;
            case Protocol.TYPE_GROUP_DEMOTE:   handleGroupDemote(c,s,d); break;
            case Protocol.TYPE_FILE_TRANSFER:  handleFileTransfer(c,s,d); break;
            case Protocol.TYPE_FILE_CHUNK:     handleFileChunk(c,s,d); break;
            case Protocol.TYPE_VOICE_CALL_START: case Protocol.TYPE_VOICE_CALL_ACCEPT:
            case Protocol.TYPE_VOICE_CALL_REJECT: case Protocol.TYPE_VOICE_CALL_END:
            case Protocol.TYPE_VIDEO_CALL_START: case Protocol.TYPE_VIDEO_CALL_ACCEPT:
            case Protocol.TYPE_VIDEO_CALL_REJECT: case Protocol.TYPE_VIDEO_CALL_END:
                handleCallSignal(c, s, type, d); break;
            case Protocol.TYPE_CONVERSATION_DELETE: handleConversationDelete(c,s,d); break;
            case Protocol.TYPE_VIDEO_FRAME:
                handleVideoFrame(c, s, d); break;
            default: sendError(c,400,"Unknown: "+type);
            
        }
    }

    private void handleConversationDelete(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId <= 0) return;
        long convId = d.has("conversationId") ? d.get("conversationId").getAsLong() : -1;
        if (convId <= 0) { sendError(c, 400, "conversationId required"); return; }
        boolean success = ConversationService.hideConversation(convId, s.userId);
        send(c, success ? ok() : err(400, "Failed to delete conversation"));
    }

    private void handleVideoFrame(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId <= 0)
            return;
        long rid = d.has("receiverId") ? d.get("receiverId").getAsLong() : -1;
        String frame = d.has("frame") ? d.get("frame").getAsString() : "";
        if (rid <= 0 || frame.isEmpty())
            return;
        WebSocket rc = findUser(rid);
        if (rc != null) {
            JsonObject fw = new JsonObject();
            fw.addProperty("type", "VIDEO_FRAME");
            fw.addProperty("senderId", s.userId);
            fw.addProperty("frame", frame);
            fw.addProperty("seq", d.has("seq") ? d.get("seq").getAsLong() : 0);
            rc.send(gson.toJson(fw));
        }
    }

    // === Helpers ===

    private void send(WebSocket c, JsonObject m) { if (c.isOpen()) c.send(gson.toJson(m)); }
    private void sendError(WebSocket c, int code, String msg) {
        JsonObject e = new JsonObject(); e.addProperty("type","ERROR"); e.addProperty("code",code); e.addProperty("message",msg); send(c,e);
    }
    private JsonObject ok() { JsonObject r=new JsonObject(); r.addProperty("type","SUCCESS"); r.addProperty("code",200); return r; }

    private String preview(String text) {
        if (text == null) return "null";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > 160 ? oneLine.substring(0, 160) + "..." : oneLine;
    }

    private void broadcastPresence(WebSocket self, long uid, String p) {
        JsonObject b = new JsonObject(); b.addProperty("type","PRESENCE"); b.addProperty("userId",uid); b.addProperty("presence",p);
        String js = gson.toJson(b);
        for (WebSocket ws : getConnections()) { if (ws != self && ws.isOpen()) ws.send(js); }
    }

    private WebSocket findUser(long uid) {
        for (Map.Entry<WebSocket,WsSession> e : sessions.entrySet())
            if (e.getValue().userId == uid && e.getKey().isOpen()) return e.getKey();
        return null;
    }

    // === Auth ===

    private void handleRegister(WebSocket c, WsSession s, JsonObject d) {
        String un = d.has("username")?d.get("username").getAsString():"";
        String pw = d.has("password")?d.get("password").getAsString():"";
        String dn = d.has("displayName")?d.get("displayName").getAsString():un;
        if (un.isEmpty()||pw.isEmpty()) { sendError(c,400,"Required"); return; }
        var r = AuthService.register(un,pw,dn);
        if (r.isSuccess()) { JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.add("data",gson.toJsonTree(r.getUser())); send(c,o); }
        else sendError(c,409,r.getMessage());
    }

    private void handleLogin(WebSocket c, WsSession s, JsonObject d) {
        String un = d.has("username")?d.get("username").getAsString():"";
        String pw = d.has("password")?d.get("password").getAsString():"";
        if (un.isEmpty()||pw.isEmpty()) { sendError(c,400,"Required"); return; }
        var r = AuthService.login(un,pw);
        if (r.isSuccess()) {
            s.userId=r.getUser().getId(); s.username=r.getUser().getUsername(); r.getUser().setPresence("ONLINE");
            chatServer.registerClient(s.userId,null);
            AuthService.updatePresence(s.userId,"ONLINE");
            broadcastPresence(c,s.userId,"ONLINE");
            JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.add("data",gson.toJsonTree(r.getUser())); send(c,o);
        } else sendError(c,401,r.getMessage());
    }

    private void handleLogout(WebSocket c, WsSession s) {
        if (s.userId>0) { AuthService.updatePresence(s.userId,"OFFLINE"); broadcastPresence(c,s.userId,"OFFLINE"); chatServer.removeClient(s.userId); }
        s.userId=-1; s.username=""; send(c,ok());
    }

    private void handleUpdateProfile(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        String dn = d.has("displayName")?d.get("displayName").getAsString():null;
        String av = d.has("avatarUrl")?d.get("avatarUrl").getAsString():null;
        if (AuthService.updateProfile(s.userId,dn,av)) {
            User u = AuthService.getUserById(s.userId);
            JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.addProperty("message","Profile updated");
            if (u!=null) o.add("data",gson.toJsonTree(u)); send(c,o);
        } else sendError(c,400,"Failed");
    }

    private void handleSearchUser(WebSocket c, WsSession s, JsonObject d) {
        String kw = d.has("keyword")?d.get("keyword").getAsString():"";
        if (kw.isEmpty()) { sendError(c,400,"Keyword required"); return; }
        var results = AuthService.searchUsers(kw);
        JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS");
        JsonObject w=new JsonObject(); w.add("results",gson.toJsonTree(results)); o.add("data",w); send(c,o);
    }

    // === Messages ===

    private void handleDirectMessage(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) { sendError(c,401,"Not logged in"); return; }
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        String ct = d.has("content")?d.get("content").getAsString():"";
        String mt = d.has("messageType")?d.get("messageType").getAsString():"TEXT";
        if (rid<=0||ct.isEmpty()) { sendError(c,400,"Required"); return; }
        long cid = ConversationService.getOrCreatePrivateConversation(s.userId,rid);
        Message m = new Message(); m.setConversationId(cid); m.setSenderId(s.userId); m.setContent(ct); m.setMessageType(mt);
        long mid = MessageService.saveMessage(m);
        long ts = System.currentTimeMillis();
        // Reply support
        long replyTo = d.has("replyToId") ? d.get("replyToId").getAsLong() : -1;
        String replyContent = d.has("replyToContent") ? d.get("replyToContent").getAsString() : "";
        String replySender = d.has("replyToSender") ? d.get("replyToSender").getAsString() : "";
        WebSocket rc = findUser(rid);
        if (rc!=null) {
            JsonObject fw=new JsonObject(); fw.addProperty("type","MESSAGE"); fw.addProperty("messageId",mid);
            fw.addProperty("senderId",s.userId); fw.addProperty("senderName",s.username);
            fw.addProperty("content",ct); fw.addProperty("messageType",mt); fw.addProperty("timestamp",ts);
            if (replyTo > 0) {
                fw.addProperty("replyToId", replyTo);
                fw.addProperty("replyToContent", replyContent);
                fw.addProperty("replyToSender", replySender);
            }
            rc.send(gson.toJson(fw));
        }
        JsonObject st=new JsonObject(); st.addProperty("type","MESSAGE_STATUS"); st.addProperty("messageId",mid);
        st.addProperty("receiverId",rid); st.addProperty("status",rc!=null?"DELIVERED":"SENT"); send(c,st);
    }

    private void handleGetHistory(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) { sendError(c,401,"Not logged in"); return; }
        long cid = d.has("conversationId")?d.get("conversationId").getAsLong():-1;
        long fid = d.has("friendId")?d.get("friendId").getAsLong():-1;
        int limit = d.has("limit")?d.get("limit").getAsInt():50;
        long before = d.has("beforeId")?d.get("beforeId").getAsLong():-1;
        if (cid<=0&&fid>0) cid=ConversationService.findPrivateConversation(s.userId,fid);
        if (cid<=0) cid=d.has("groupId")?d.get("groupId").getAsLong():-1;
        if (cid<=0) { sendError(c,404,"Not found"); return; }
        var msgs = MessageService.getMessages(cid,limit,before);
        JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS");
        JsonArray arr=new JsonArray();
        for (var m:msgs) { JsonObject mo=new JsonObject(); mo.addProperty("id",m.getId()); mo.addProperty("senderId",m.getSenderId());
            mo.addProperty("senderName",m.getSenderDisplayName()); mo.addProperty("content",m.getContent());
            mo.addProperty("messageType",m.getMessageType()); mo.addProperty("status",m.getStatus()); mo.addProperty("timestamp",m.getCreatedAt().getTime()); arr.add(mo); }
        JsonObject wrap=new JsonObject(); wrap.addProperty("conversationId",cid);
        if (d.has("friendId")) wrap.addProperty("friendId",d.get("friendId").getAsLong());
        if (d.has("groupId")) wrap.addProperty("groupId",d.get("groupId").getAsLong());
        wrap.add("messages",arr); o.add("data",wrap); send(c,o);
    }

    private void handleTyping(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        boolean ty = d.has("typing")&&d.get("typing").getAsBoolean();
        WebSocket rc = findUser(rid);
        if (rc!=null) { JsonObject fw=new JsonObject(); fw.addProperty("type","TYPING"); fw.addProperty("senderId",s.userId); fw.addProperty("typing",ty); rc.send(gson.toJson(fw)); }
    }

    private void handleUnsend(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        long mid = d.has("messageId")?d.get("messageId").getAsLong():-1;
        MessageService.unsendMessage(mid);
        WebSocket rc = findUser(rid);
        if (rc!=null) { JsonObject fw=new JsonObject(); fw.addProperty("type","UNSEND"); fw.addProperty("senderId",s.userId); fw.addProperty("messageId",mid); rc.send(gson.toJson(fw)); }
    }

    private void handleReaction(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        long mid = d.has("messageId")?d.get("messageId").getAsLong():-1;
        String rx = d.has("reaction")?d.get("reaction").getAsString():"";
        WebSocket rc = findUser(rid);
        if (rc!=null) { JsonObject fw=new JsonObject(); fw.addProperty("type","REACTION"); fw.addProperty("senderId",s.userId); fw.addProperty("messageId",mid); fw.addProperty("reaction",rx); rc.send(gson.toJson(fw)); }
    }

    // === Friends ===

    private void handleFriendReq(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long to = d.has("toUserId")?d.get("toUserId").getAsLong():-1;
        if (to <= 0) { sendError(c, 400, "Invalid target user"); return; }
        AuthService.sendFriendRequest(s.userId, to);
        WebSocket t = findUser(to);
        if (t!=null) { JsonObject fw=new JsonObject(); fw.addProperty("type","FRIEND_REQUEST"); fw.addProperty("fromUserId",s.userId); fw.addProperty("fromUsername",s.username); t.send(gson.toJson(fw)); }
        send(c,ok());
    }

    private void handleFriendResp(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long from = d.has("fromUserId")?d.get("fromUserId").getAsLong():-1;
        String act = d.has("action")?d.get("action").getAsString():"accept";
        if ("accept".equalsIgnoreCase(act)) {
            AuthService.addFriend(s.userId, from);
            AuthService.updateFriendRequestStatus(from, s.userId, "ACCEPTED");
        } else {
            AuthService.updateFriendRequestStatus(from, s.userId, "REJECTED");
        }
        WebSocket t = findUser(from);
        if (t!=null) { JsonObject fw=new JsonObject(); fw.addProperty("type","FRIEND_RESPONSE"); fw.addProperty("fromUserId",s.userId); fw.addProperty("fromUsername",s.username); fw.addProperty("action",act); t.send(gson.toJson(fw)); }
        send(c,ok());
    }

    private void handleGetFriendReqs(WebSocket c, WsSession s) {
        if (s.userId<=0) return;
        var reqs = AuthService.getPendingFriendRequests(s.userId);
        JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS");
        JsonArray arr=new JsonArray();
        for (var fr:reqs) { JsonObject fo=new JsonObject(); fo.addProperty("id",fr.getId()); fo.addProperty("senderId",fr.getSenderId());
            fo.addProperty("senderUsername",fr.getSenderUsername()); fo.addProperty("senderDisplayName",fr.getSenderDisplayName()); arr.add(fo); }
        JsonObject w=new JsonObject(); w.add("requests",arr); o.add("data",w); send(c,o);
    }

    private void handleFriendList(WebSocket c, WsSession s) {
        if (s.userId<=0) return;
        var friends = AuthService.getFriends(s.userId);
        JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS");
        JsonObject wrap=new JsonObject();
        wrap.add("friends",gson.toJsonTree(friends));
        o.add("data",wrap);
        send(c,o);
    }

    // === Groups ===

    private void handleGroupCreate(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        String n = d.has("name")?d.get("name").getAsString():"";
        var r = GroupService.createGroup(n,s.userId);
        if (r.isSuccess()) { JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.add("data",gson.toJsonTree(r.getGroup())); send(c,o); }
        else sendError(c,400,r.getMessage());
    }

    private void handleGroupMsg(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        String ct = d.has("content")?d.get("content").getAsString():"";
        String mt = d.has("messageType")?d.get("messageType").getAsString():"TEXT";
        if (gid<=0||ct.isEmpty()) { sendError(c,400,"groupId and content are required"); return; }
        long mid = GroupService.saveGroupMessage(s.userId,gid,ct,mt);
        var members = GroupService.getGroupMembers(gid);
        JsonObject fw=new JsonObject(); fw.addProperty("type","GROUP_MESSAGE"); fw.addProperty("messageId",mid); fw.addProperty("groupId",gid);
        fw.addProperty("senderId",s.userId); fw.addProperty("senderName",s.username); fw.addProperty("content",ct); fw.addProperty("messageType",mt); fw.addProperty("timestamp",System.currentTimeMillis());
        String js = gson.toJson(fw);
        for (var m:members) { if (m.getUserId()!=s.userId) { WebSocket mc=findUser(m.getUserId()); if (mc!=null) mc.send(js); } }
    }

    private void handleGroupJoin(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        send(c, GroupService.addMember(gid,s.userId)?ok():err(400,"Failed"));
    }

    private void handleGroupLeave(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        send(c, GroupService.removeMember(gid,s.userId)?ok():err(400,"Failed"));
    }

    private void handleGroupList(WebSocket c, WsSession s) {
        if (s.userId<=0) return;
        var gs = GroupService.getUserGroups(s.userId);
        JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.add("data",gson.toJsonTree(gs)); send(c,o);
    }

    private void handleGroupInfo(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        if (gid<=0) { sendError(c,400,"groupId required"); return; }
        JsonObject info = GroupService.getGroupInfo(gid);
        if (info.has("id")) { JsonObject o=new JsonObject(); o.addProperty("type","SUCCESS"); o.add("data",info); send(c,o); }
        else sendError(c,404,"Not found");
    }

    private void handleGroupUpdate(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        if (gid<=0) { sendError(c,400,"groupId required"); return; }
        String name = d.has("name")?d.get("name").getAsString():null;
        String av = d.has("avatarUrl")?d.get("avatarUrl").getAsString():null;
        String desc = d.has("description")?d.get("description").getAsString():null;
        if (name==null&&av==null&&desc==null) { sendError(c,400,"Nothing to update"); return; }
        if (GroupService.updateGroup(gid,name,av,desc)) {
            var members = GroupService.getGroupMembers(gid);
            JsonObject fw = new JsonObject();
            fw.addProperty("type","GROUP_UPDATE"); fw.addProperty("groupId",gid);
            if (name!=null) fw.addProperty("name",name);
            if (av!=null) fw.addProperty("avatarUrl",av);
            if (desc!=null) fw.addProperty("description",desc);
            String js = gson.toJson(fw);
            for (var m : members) { if (m.getUserId()!=s.userId) { WebSocket mc=findUser(m.getUserId()); if (mc!=null) mc.send(js); } }
            send(c,ok());
        } else { sendError(c,400,"Failed to update"); }
    }

    private void handleGroupRemoveMember(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        long tid = d.has("targetUserId")?d.get("targetUserId").getAsLong():-1;
        if (gid<=0||tid<=0) { sendError(c,400,"groupId and targetUserId required"); return; }
        var members = GroupService.getGroupMembers(gid);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == s.userId && m.isAdmin());
        if (!isAdmin) { sendError(c,401,"Only admins can remove members"); return; }
        if (GroupService.removeMember(gid, tid)) {
            WebSocket t = findUser(tid);
            if (t!=null) { JsonObject n=new JsonObject(); n.addProperty("type","GROUP_LEAVE"); n.addProperty("groupId",gid); n.addProperty("kicked",true); t.send(gson.toJson(n)); }
            send(c,ok());
        } else { sendError(c,400,"Failed"); }
    }

    private void handleGroupPromote(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        long tid = d.has("targetUserId")?d.get("targetUserId").getAsLong():-1;
        if (gid<=0||tid<=0) { sendError(c,400,"groupId and targetUserId required"); return; }
        var members = GroupService.getGroupMembers(gid);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == s.userId && m.isAdmin());
        if (!isAdmin) { sendError(c,401,"Only admins can promote"); return; }
        send(c, GroupService.promoteMember(gid,tid) ? ok() : err(400,"Failed"));
    }

    private void handleGroupDemote(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long gid = d.has("groupId")?d.get("groupId").getAsLong():-1;
        long tid = d.has("targetUserId")?d.get("targetUserId").getAsLong():-1;
        if (gid<=0||tid<=0) { sendError(c,400,"groupId and targetUserId required"); return; }
        var members = GroupService.getGroupMembers(gid);
        boolean isAdmin = members.stream().anyMatch(m -> m.getUserId() == s.userId && m.isAdmin());
        if (!isAdmin) { sendError(c,401,"Only admins can demote"); return; }
        send(c, GroupService.demoteMember(gid,tid) ? ok() : err(400,"Failed"));
    }

    // === Files ===

    private void handleFileTransfer(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        String fid = d.has("fileId")?d.get("fileId").getAsString():"";
        String fn = d.has("fileName")?d.get("fileName").getAsString():"file";
        int tc = d.has("totalChunks")?d.get("totalChunks").getAsInt():1;
        long fs = d.has("fileSize")?d.get("fileSize").getAsLong():0;
        WebSocket rc = findUser(rid);
        if (rc!=null) {
            JsonObject nf=new JsonObject(); nf.addProperty("type","FILE_TRANSFER"); nf.addProperty("senderId",s.userId);
            nf.addProperty("senderName",s.username); nf.addProperty("fileId",fid); nf.addProperty("fileName",fn);
            nf.addProperty("totalChunks",tc); nf.addProperty("fileSize",fs); rc.send(gson.toJson(nf));
            WsSession rs = sessions.get(rc);
            if (rs!=null) rs.pendingFiles.put(fid,new FileReceiver(fid,fn,tc,fs));
        }
        send(c,ok());
    }

    private void handleFileChunk(WebSocket c, WsSession s, JsonObject d) {
        if (s.userId<=0) return;
        long rid = d.has("receiverId")?d.get("receiverId").getAsLong():-1;
        String fid = d.has("fileId")?d.get("fileId").getAsString():"";
        int ci = d.has("chunkIndex")?d.get("chunkIndex").getAsInt():0;
        String b64 = d.has("data")?d.get("data").getAsString():"";
        WebSocket rc = findUser(rid);
        if (rc!=null) {
            JsonObject fw=new JsonObject(); fw.addProperty("type","FILE_CHUNK"); fw.addProperty("senderId",s.userId);
            fw.addProperty("fileId",fid); fw.addProperty("chunkIndex",ci); fw.addProperty("data",b64); rc.send(gson.toJson(fw));
            WsSession rs = sessions.get(rc);
            if (rs!=null) { FileReceiver fr=rs.pendingFiles.get(fid);
                if (fr!=null) { try { byte[] cb=Base64.getDecoder().decode(b64); try(FileOutputStream fos=new FileOutputStream(fr.filePath,true)){fos.write(cb);} fr.receivedChunks++; if (fr.isComplete()) rs.pendingFiles.remove(fid); } catch(IOException ex){logger.error("Chunk error",ex);} }
            }
        }
        JsonObject ack=new JsonObject(); ack.addProperty("type","SUCCESS"); ack.addProperty("fileId",fid); ack.addProperty("chunkIndex",ci); send(c,ack);
    }

    // === Calls ===

    private void handleCallSignal(WebSocket c, WsSession s, String type, JsonObject d) {
        if (s.userId<=0) return;
        long tid = d.has("targetId")?d.get("targetId").getAsLong():-1;
        WebSocket t = findUser(tid);

        // Khi chấp nhận cuộc gọi, tạo relay session
        if (type.contains("ACCEPT") && t != null) {
            boolean isVideo = type.contains("VIDEO");
            String sid = String.format("%08d", Math.abs((s.userId * 31 + tid * 17 + System.currentTimeMillis()) % 100000000));

            // RELAY_INFO cho người gọi (target)
            JsonObject riA = new JsonObject();
            riA.addProperty("type","RELAY_INFO"); riA.addProperty("sessionId",sid);
            riA.addProperty("voicePort",MediaRelayServer.VOICE_RELAY_PORT);
            riA.addProperty("videoPort",MediaRelayServer.VIDEO_RELAY_PORT);
            riA.addProperty("mediaTcpPort", MediaRelayServer.MEDIA_TCP_PORT);
            riA.addProperty("isVideo",isVideo); riA.addProperty("targetId",tid);
            riA.addProperty("senderId",s.userId); riA.addProperty("senderName",s.username);
            riA.addProperty("caller",true);
            t.send(gson.toJson(riA));

            // RELAY_INFO cho người chấp nhận (sender)
            JsonObject riB = new JsonObject();
            riB.addProperty("type","RELAY_INFO"); riB.addProperty("sessionId",sid);
            riB.addProperty("voicePort",MediaRelayServer.VOICE_RELAY_PORT);
            riB.addProperty("videoPort",MediaRelayServer.VIDEO_RELAY_PORT);
            riB.addProperty("mediaTcpPort", MediaRelayServer.MEDIA_TCP_PORT);
            riB.addProperty("isVideo",isVideo); riB.addProperty("targetId",s.userId);
            riB.addProperty("senderId",tid); riB.addProperty("senderName",s.username);
            riB.addProperty("caller",false);
            send(c,riB);
            return;
        }

        // Dọn relay session khi kết thúc
        if (type.contains("END") || type.contains("REJECT")) {
            if (d.has("sessionId")) {
                String sid = d.get("sessionId").getAsString();
                chatServer.getMediaRelay().unregister(sid);
                if (d.has("isVideo") && d.get("isVideo").getAsBoolean())
                    chatServer.getMediaRelay().unregister(sid, true);
            }
        }

        // Forward tín hiệu
        if (t!=null) {
            JsonObject fw=new JsonObject();
            fw.addProperty("type",type); fw.addProperty("senderId",s.userId);
            fw.addProperty("senderName",s.username);
            if(d.has("port"))fw.addProperty("port",d.get("port").getAsInt());
            if(d.has("sessionId"))fw.addProperty("sessionId",d.get("sessionId").getAsString());
            if(d.has("isVideo"))fw.addProperty("isVideo",d.get("isVideo").getAsBoolean());
            t.send(gson.toJson(fw));
        } else {
            JsonObject r=new JsonObject();
            r.addProperty("type",type.contains("REJECT")?type:type+"_REJECT");
            r.addProperty("code",410); r.addProperty("message","Offline");
            send(c,r);
        }
    }

    private JsonObject err(int code, String msg) { JsonObject o=new JsonObject(); o.addProperty("type","ERROR"); o.addProperty("code",code); o.addProperty("message",msg); return o; }
}
