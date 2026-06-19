package com.messenger.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

/**
 * Centralized app configuration — reads from app.properties.
 * Edit app.properties to change server URL (ngrok / local).
 */
public final class AppConfig {

    private static final String PROPERTIES_FILE = "/app.properties";
    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("[AppConfig] Cannot load " + PROPERTIES_FILE + ": " + e.getMessage());
        }
    }

    private AppConfig() {}

    // ==================== Getters ====================

    /** e.g. https://cassette-ultimatum-swiftly.ngrok-free.dev */
    public static String getBaseUrl() {
        return props.getProperty("server.baseUrl", "http://localhost:9080").trim();
    }

    /** e.g. /api */
    public static String getApiPath() {
        return props.getProperty("server.apiPath", "/api");
    }

    /** e.g. /ws */
    public static String getWsPath() {
        return props.getProperty("server.wsPath", "/ws");
    }

    /** Full REST API base URL: https://xxx.ngrok-free.dev/api */
    public static String getApiBaseUrl() {
        String base = getBaseUrl().replaceAll("/+$", "");
        String api = getApiPath();
        if (api.isEmpty()) return base;
        return base + (api.startsWith("/") ? api : "/" + api);
    }

    /** Build a REST URI: /api/login → https://xxx.ngrok-free.dev/api/login */
    public static URI apiUri(String path) {
        String full = getApiBaseUrl() + (path.startsWith("/") ? path : "/" + path);
        return URI.create(full);
    }

    /** Build WebSocket URI. Auto-converts https→wss, http→ws */
    public static URI wsUri() {
        String base = getBaseUrl().replaceAll("/+$", "");
        String wsPath = getWsPath();
        if (wsPath.isEmpty()) wsPath = "/ws";

        // Convert protocol: https → wss, http → ws
        if (base.startsWith("https://")) {
            base = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            base = "ws://" + base.substring("http://".length());
        }
        return URI.create(base + (wsPath.startsWith("/") ? wsPath : "/" + wsPath));
    }

    /** Quick check if using ngrok (HTTPS URL) */
    public static boolean isNgrok() {
        return getBaseUrl().contains("ngrok");
    }
}
