package com.messenger.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatHistoryManager {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryManager.class);
    private static final Gson gson = new Gson();
    private static final String HISTORY_DIR = "chat_history";
    private static final Path HISTORY_PATH = Paths.get(HISTORY_DIR);
    private static final Map<String, List<ChatMessage>> cache = new ConcurrentHashMap<>();

    private ChatHistoryManager() {}

    public static class ChatMessage {
        public long senderId;
        public String senderName;
        public String content;
        public long timestamp;
        public boolean isMine;
        public String messageType;
        public String status;
        public long messageId;

        public ChatMessage() {}

        public ChatMessage(long senderId, String senderName, String content, long timestamp, boolean isMine) {
            this(senderId, senderName, content, timestamp, isMine, "TEXT", "SENT", timestamp);
        }

        public ChatMessage(long senderId, String senderName, String content, long timestamp, boolean isMine,
                           String messageType, String status, long messageId) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.content = content;
            this.timestamp = timestamp;
            this.isMine = isMine;
            this.messageType = messageType != null ? messageType : "TEXT";
            this.status = status != null ? status : "SENT";
            this.messageId = messageId;
        }
    }

    private static String conversationKey(long uid1, long uid2) {
        long a = Math.min(uid1, uid2);
        long b = Math.max(uid1, uid2);
        return a + "_" + b;
    }

    private static Path filePath(long uid1, long uid2) {
        return HISTORY_PATH.resolve(conversationKey(uid1, uid2) + ".json");
    }

    private static void ensureDir() {
        try { Files.createDirectories(HISTORY_PATH); }
        catch (IOException e) { logger.warn("Không thể tạo thư mục {}: {}", HISTORY_DIR, e.getMessage()); }
    }

    public static synchronized List<ChatMessage> loadHistory(long myId, long friendId) {
        String key = conversationKey(myId, friendId);
        if (cache.containsKey(key)) return new ArrayList<>(cache.get(key));

        Path file = filePath(myId, friendId);
        if (!Files.exists(file)) { cache.put(key, new ArrayList<>()); return new ArrayList<>(); }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type type = new TypeToken<List<ChatMessage>>(){}.getType();
            List<ChatMessage> msgs = gson.fromJson(json, type);
            if (msgs == null) msgs = new ArrayList<>();
            cache.put(key, msgs);
            logger.info("Đã load {} tin nhắn từ {}", msgs.size(), file.getFileName());
            return new ArrayList<>(msgs);
        } catch (IOException e) {
            logger.error("Lỗi đọc lịch sử chat: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public static synchronized void saveMessage(long myId, long friendId,
                                                 long senderId, String senderName,
                                                 String content, long timestamp, boolean isMine) {
        saveMessage(myId, friendId, senderId, senderName, content, timestamp, isMine, "TEXT", "SENT", timestamp);
    }

    public static synchronized void saveMessage(long myId, long friendId,
                                                 long senderId, String senderName,
                                                 String content, long timestamp, boolean isMine,
                                                 String messageType, String status, long messageId) {
        ensureDir();
        String key = conversationKey(myId, friendId);
        if (!cache.containsKey(key)) loadHistory(myId, friendId);
        cache.get(key).add(new ChatMessage(senderId, senderName, content, timestamp, isMine, messageType, status, messageId));
        writeFile(key, myId, friendId);
    }

    private static void writeFile(String key, long myId, long friendId) {
        try {
            Path file = filePath(myId, friendId);
            Files.writeString(file, gson.toJson(cache.get(key)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Lỗi ghi lịch sử chat: {}", e.getMessage());
        }
    }

    public static void clearCache() { cache.clear(); }
}
