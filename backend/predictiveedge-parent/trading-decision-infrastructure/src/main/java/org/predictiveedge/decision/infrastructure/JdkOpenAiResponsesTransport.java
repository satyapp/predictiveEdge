package org.predictiveedge.decision.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Minimal Responses API transport that never logs the API key or response body. */
public final class JdkOpenAiResponsesTransport implements OpenAiResponsesTransport {
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;

    public JdkOpenAiResponsesTransport(HttpClient http, URI endpoint, Duration timeout) {
        this.http = Objects.requireNonNull(http, "HTTP client is required");
        this.endpoint = Objects.requireNonNull(endpoint, "OpenAI endpoint is required");
        this.timeout = Objects.requireNonNull(timeout, "OpenAI timeout is required");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("OpenAI timeout must be positive");
    }

    @Override
    public String createResponse(String apiKey, String requestJson) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("OpenAI API key is required");
        if (requestJson == null || requestJson.isBlank()) throw new IllegalArgumentException("OpenAI request is required");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI Responses API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAI Responses API is unavailable", exception);
        }
    }
}
