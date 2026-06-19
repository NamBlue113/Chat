package com.messenger.shared;

/**
 * Central protocol definition for the Messenger application.
 * Defines all message types, status codes, and constants used
 * for communication between client and server.
 */
public final class Protocol {

    private Protocol() {
        throw new UnsupportedOperationException("Protocol is a utility class and cannot be instantiated");
    }

    // ==================== Server Ports ====================
    public static final int TCP_PORT = 9000;
    public static final int VOICE_PORT = 9001;
    public static final int VIDEO_PORT = 9002;
    public static final int FILE_PORT = 9003;

    // ==================== Message Types ====================

    // Authentication
    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_LOGIN = "LOGIN";
    public static final String TYPE_LOGOUT = "LOGOUT";
    public static final String TYPE_UPDATE_PROFILE = "UPDATE_PROFILE";

    // Chat messages
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_GET_MESSAGE_HISTORY = "GET_MESSAGE_HISTORY";
    public static final String TYPE_TYPING = "TYPING";
    public static final String TYPE_MESSAGE_STATUS = "MESSAGE_STATUS";
    public static final String TYPE_UNSEND = "UNSEND";
    public static final String TYPE_REACTION = "REACTION";

    // File transfer
    public static final String TYPE_FILE_TRANSFER = "FILE_TRANSFER";
    public static final String TYPE_FILE_CHUNK = "FILE_CHUNK";

    // Friend system
    public static final String TYPE_SEARCH_USER = "SEARCH_USER";
    public static final String TYPE_FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String TYPE_FRIEND_RESPONSE = "FRIEND_RESPONSE";
    public static final String TYPE_FRIEND_LIST = "FRIEND_LIST";
    public static final String TYPE_GET_FRIEND_REQUESTS = "GET_FRIEND_REQUESTS";
    public static final String TYPE_PRESENCE = "PRESENCE";

    // Conversation list
    public static final String TYPE_CONVERSATION_LIST = "CONVERSATION_LIST";
    public static final String TYPE_CONVERSATION_DELETE = "CONVERSATION_DELETE";

    // Group chat
    public static final String TYPE_GROUP_CREATE = "GROUP_CREATE";
    public static final String TYPE_GROUP_JOIN = "GROUP_JOIN";
    public static final String TYPE_GROUP_LEAVE = "GROUP_LEAVE";
    public static final String TYPE_GROUP_MESSAGE = "GROUP_MESSAGE";
    public static final String TYPE_GROUP_LIST = "GROUP_LIST";
    public static final String TYPE_GROUP_INFO = "GROUP_INFO";
    public static final String TYPE_GROUP_UPDATE = "GROUP_UPDATE";
    public static final String TYPE_GROUP_REMOVE_MEMBER = "GROUP_REMOVE_MEMBER";
    public static final String TYPE_GROUP_PROMOTE = "GROUP_PROMOTE";
    public static final String TYPE_GROUP_DEMOTE = "GROUP_DEMOTE";

    // Voice call
    public static final String TYPE_VOICE_CALL_START = "VOICE_CALL_START";
    public static final String TYPE_VOICE_CALL_ACCEPT = "VOICE_CALL_ACCEPT";
    public static final String TYPE_VOICE_CALL_REJECT = "VOICE_CALL_REJECT";
    public static final String TYPE_VOICE_CALL_END = "VOICE_CALL_END";
    public static final String TYPE_VIDEO_FRAME = "VIDEO_FRAME";
    public static final String TYPE_VOICE_DATA = "VOICE_DATA";

    // Video call
    public static final String TYPE_VIDEO_CALL_START = "VIDEO_CALL_START";
    public static final String TYPE_VIDEO_CALL_ACCEPT = "VIDEO_CALL_ACCEPT";
    public static final String TYPE_VIDEO_CALL_REJECT = "VIDEO_CALL_REJECT";
    public static final String TYPE_VIDEO_CALL_END = "VIDEO_CALL_END";
 

    // Relay signaling (voice/video)
    public static final String TYPE_RELAY_INFO = "RELAY_INFO";

    // Generic responses
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_SUCCESS = "SUCCESS";

    // ==================== Message Status ====================
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_SEEN = "SEEN";

    // ==================== Presence ====================
    public static final String PRESENCE_ONLINE = "ONLINE";
    public static final String PRESENCE_OFFLINE = "OFFLINE";

    // ==================== Message Content Types ====================
    public static final String CONTENT_TEXT = "TEXT";
    public static final String CONTENT_IMAGE = "IMAGE";
    public static final String CONTENT_FILE = "FILE";
    public static final String CONTENT_VIDEO = "VIDEO";
    public static final String CONTENT_VOICE = "VOICE";
    public static final String CONTENT_STICKER = "STICKER";

    // ==================== Reactions ====================
    public static final String REACTION_HEART = "\u2764\uFE0F";
    public static final String REACTION_LIKE = "\uD83D\uDC4D";
    public static final String REACTION_HAHA = "\uD83D\uDE02";
    public static final String REACTION_WOW = "\uD83D\uDE2E";
    public static final String REACTION_SAD = "\uD83D\uDE22";
    public static final String REACTION_ANGRY = "\uD83D\uDE21";

    // ==================== Friend Request Status ====================
    public static final String FR_PENDING = "PENDING";
    public static final String FR_ACCEPTED = "ACCEPTED";
    public static final String FR_REJECTED = "REJECTED";

    // ==================== Group Member Roles ====================
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    // ==================== Response Codes ====================
    public static final int CODE_OK = 200;
    public static final int CODE_ERROR = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_USER_EXISTS = 409;
    public static final int CODE_USER_OFFLINE = 410;
    public static final int CODE_ALREADY_FRIENDS = 411;

    // ==================== File Transfer ====================
    public static final int FILE_CHUNK_SIZE = 8192;
    public static final int MIN_CHUNK_SIZE = 4096;
    public static final int MAX_FILE_SIZE_MB = 100;

    // ==================== Networking ====================
    public static final int SOCKET_TIMEOUT_MS = 30000;
    public static final int HEARTBEAT_INTERVAL_MS = 15000;
    public static final int MAX_MESSAGE_LENGTH = 1048576;
    public static final int MAX_RECONNECT_ATTEMPTS = 5;

    // ==================== Delimiter ====================
    public static final String DELIMITER = "\n";
}
