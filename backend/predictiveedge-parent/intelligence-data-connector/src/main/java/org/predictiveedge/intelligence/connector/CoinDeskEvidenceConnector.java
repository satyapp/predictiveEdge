package org.predictiveedge.intelligence.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class CoinDeskEvidenceConnector implements EvidenceSourceConnector {
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;

    public CoinDeskEvidenceConnector(HttpClient http, URI endpoint, String apiKey, Duration timeout) {
        this.http = java.util.Objects.requireNonNull(http, "HTTP client is required");
        this.endpoint = java.util.Objects.requireNonNull(endpoint, "CoinDesk endpoint is required");
        this.apiKey = required(apiKey, "CoinDesk API key");
        this.timeout = java.util.Objects.requireNonNull(timeout, "CoinDesk timeout is required");
    }

    @Override public EvidenceSource source() { return EvidenceSource.COINDESK; }
    @Override public boolean supports(EvidenceDomain domain) { return domain == EvidenceDomain.NEWS_AND_SENTIMENT; }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (!supports(domain)) throw new IllegalArgumentException("CoinDesk supports news and sentiment only");
        URI requestUri = URI.create(endpoint + "?search_string=" + encode(query.naturalLanguageQuery())
                + "&source_key=coindesk&lang=EN&limit=" + encode(query.attribute("coinDeskLimit", "10"))
                + "&to_ts=" + query.cutoff().getEpochSecond());
        var request = HttpRequest.newBuilder(requestUri).timeout(timeout).header("Accept", "application/json")
                .header("x-api-key", apiKey).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new EvidenceUnavailableException("CoinDesk returned HTTP " + response.statusCode());
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EvidenceUnavailableException("CoinDesk request was interrupted");
        } catch (IOException failure) {
            throw new EvidenceUnavailableException("CoinDesk request failed");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
