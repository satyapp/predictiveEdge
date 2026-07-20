package org.predictiveedge.broker.zerodha;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.predictiveedge.broker.domain.BrokerFailure;

public final class JdkZerodhaTransport implements ZerodhaTransport, ZerodhaTokenExchangeTransport,
        ZerodhaSessionTerminationTransport {
    private final HttpClient client;

    public JdkZerodhaTransport(HttpClient client) {
        this.client = java.util.Objects.requireNonNull(client, "HTTP client is required");
    }

    @Override
    public String get(URI uri, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET();
        headers.forEach(request::header);
        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "Zerodha request failed with HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha request could not be completed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha request was interrupted");
        }
    }

    @Override
    public String postForm(URI uri, Map<String, String> headers, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "Zerodha token exchange failed with HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha token exchange could not be completed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha token exchange was interrupted");
        }
    }

    @Override
    public int delete(URI uri, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).DELETE();
        headers.forEach(request::header);
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException failure) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha session invalidation could not be completed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha session invalidation was interrupted");
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
