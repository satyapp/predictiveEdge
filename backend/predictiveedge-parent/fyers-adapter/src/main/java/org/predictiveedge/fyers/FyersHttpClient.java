package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class FyersHttpClient {
    private static final URI API = URI.create("https://api-t1.fyers.in/api/v3/");
    private static final URI DATA = URI.create("https://api-t1.fyers.in/data/");
    private final FyersTransport transport;
    private final ObjectMapper json;
    private final Duration timeout;
    FyersHttpClient(ObjectMapper json, Duration timeout) {
        this(json, timeout, new JdkFyersTransport(timeout));
    }
    FyersHttpClient(ObjectMapper json, Duration timeout, FyersTransport transport) {
        this.json = json;
        this.timeout = timeout;
        this.transport = transport;
    }

    JsonNode exchange(String appIdHash, String code) {
        var body = json.createObjectNode().put("grant_type", "authorization_code")
                .put("appIdHash", appIdHash).put("code", code);
        return send(HttpRequest.newBuilder(API.resolve("validate-authcode"))
                .header("Content-Type", "application/json").header("User-Agent", "PredictiveEdge/0.1")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }
    JsonNode profile(String authorization) { return get(API.resolve("profile"), authorization); }
    JsonNode screenersConfig(String authorization) { return get(API.resolve("screeners/config"), authorization); }
    JsonNode standardScreener(String authorization, Map<String, String> parameters) {
        return get(uri(API.resolve("screeners/standard"), parameters), authorization);
    }
    JsonNode quotes(String authorization, String symbols) {
        return get(uri(DATA.resolve("quotes"), Map.of("symbols", symbols)), authorization);
    }
    JsonNode history(String authorization, Map<String, String> parameters) {
        return get(uri(DATA.resolve("history"), parameters), authorization);
    }
    JsonNode depth(String authorization, String symbol) {
        return get(uri(DATA.resolve("depth"), Map.of("symbol", symbol, "ohlcv_flag", "1")), authorization);
    }
    JsonNode marketStatus(String authorization) { return get(DATA.resolve("marketStatus"), authorization); }
    JsonNode publicJson(URI uri) {
        return get(uri, null);
    }
    private JsonNode get(URI uri, String authorization) {
        var request = HttpRequest.newBuilder(uri).header("Accept", "application/json")
                .header("User-Agent", "PredictiveEdge/0.1").timeout(timeout);
        if (authorization != null && !authorization.isBlank()) request.header("Authorization", authorization);
        return send(request.GET().build());
    }
    private JsonNode send(HttpRequest request) {
        try {
            FyersTransport.Response response = transport.send(request);
            JsonNode root;
            try { root = json.readTree(response.body()); }
            catch (Exception invalid) { throw new IllegalStateException("FYERS returned an invalid JSON response"); }
            if (response.statusCode() / 100 != 2 || "error".equalsIgnoreCase(root.path("s").asText())) {
                String message = root.path("message").asText("FYERS request was rejected");
                throw new IllegalStateException(message + " (HTTP " + response.statusCode() + ")");
            }
            return root;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FYERS request was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("FYERS is currently unreachable", failure);
        }
    }
    private static URI uri(URI base, Map<String, String> values) {
        var parameters = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> { if (value != null && !value.isBlank()) parameters.put(key, value); });
        String query = parameters.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return URI.create(base + (query.isBlank() ? "" : "?" + query));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
