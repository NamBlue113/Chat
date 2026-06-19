package com.messenger.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.messenger.client.config.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * REST API client for auth and data endpoints.
 * Uses java.net.http.HttpClient (Java 11+).
 */
public final class ApiClient {

    private static final Gson gson = new Gson();
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ApiClient() {}

    /**
     * HTTP GET — returns parsed JsonObject.
     * @throws IOException on network error or HTTP status >= 400
     */
    public static JsonObject get(String apiPath) throws IOException, InterruptedException {
        URI uri = AppConfig.apiUri(apiPath);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        String body = resp.body();
        if (body == null || body.isBlank()) return new JsonObject();
        return gson.fromJson(body, JsonObject.class);
    }

    /**
     * HTTP POST with JSON body — returns parsed JsonObject.
     * @throws IOException on network error or HTTP status >= 400
     */
    public static JsonObject post(String apiPath, JsonObject body) throws IOException, InterruptedException {
        URI uri = AppConfig.apiUri(apiPath);
        String json = gson.toJson(body);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        String respBody = resp.body();
        if (respBody == null || respBody.isBlank()) return new JsonObject();
        return gson.fromJson(respBody, JsonObject.class);
    }

    /**
     * Quick connectivity check to the server.
     */
    public static boolean isServerReachable() {
        try {
            URI uri = AppConfig.apiUri("/ping");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("ngrok-skip-browser-warning", "true")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
