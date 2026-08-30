package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/** Converts the shared strict recommendation request into Ollama's local chat API. */
public final class OllamaResponsesTransport implements OpenAiResponsesTransport {
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;
    private final int contextWindowTokens;
    private final int maximumOutputTokens;

    public OllamaResponsesTransport(ObjectMapper json, HttpClient http, URI endpoint, Duration timeout,
            int contextWindowTokens, int maximumOutputTokens) {
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.http = Objects.requireNonNull(http, "HTTP client is required");
        this.endpoint = Objects.requireNonNull(endpoint, "Ollama endpoint is required");
        this.timeout = Objects.requireNonNull(timeout, "Ollama timeout is required");
        if (contextWindowTokens < 4096) throw new IllegalArgumentException("Ollama context window must be at least 4096");
        if (maximumOutputTokens < 256) throw new IllegalArgumentException("Ollama output allowance must be at least 256");
        if (maximumOutputTokens >= contextWindowTokens) {
            throw new IllegalArgumentException("Ollama output allowance must be smaller than its context window");
        }
        this.contextWindowTokens = contextWindowTokens;
        this.maximumOutputTokens = maximumOutputTokens;
    }

    @Override
    public String createResponse(String ignoredApiKey, String sharedRequestJson) {
        try {
            JsonNode shared = json.readTree(sharedRequestJson);
            ObjectNode request = json.createObjectNode();
            request.put("model", shared.path("model").asText());
            request.put("stream", false);
            request.put("think", false);
            ArrayNode messages = request.putArray("messages");
            for (JsonNode item : shared.path("input")) {
                ObjectNode message = messages.addObject().put("role", item.path("role").asText());
                StringBuilder content = new StringBuilder();
                item.path("content").forEach(value -> content.append(value.path("text").asText()));
                message.put("content", content.toString());
            }
            request.set("format", shared.path("text").path("format").path("schema"));
            request.putObject("options").put("temperature", 0)
                    .put("num_ctx", contextWindowTokens)
                    .put("num_predict", maximumOutputTokens);
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama chat API returned HTTP " + response.statusCode());
            }
            String content = required(json.readTree(response.body()).path("message").path("content").asText(null));
            ObjectNode wrapped = json.createObjectNode();
            wrapped.putArray("output").addObject().put("type", "message").putArray("content")
                    .addObject().put("type", "output_text").put("text", content);
            return json.writeValueAsString(wrapped);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request was interrupted", exception);
        } catch (HttpTimeoutException exception) {
            throw new IllegalStateException("Ollama chat API exceeded its " + timeout.toSeconds()
                    + " second inference timeout", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Ollama chat API is unavailable", exception);
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Ollama returned no message content");
        return value;
    }
}
