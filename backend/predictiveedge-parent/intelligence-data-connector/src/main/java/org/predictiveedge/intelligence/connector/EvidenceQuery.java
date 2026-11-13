package org.predictiveedge.intelligence.connector;

import java.time.Instant;
import java.util.Map;

import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.spi.BrokerContext;

public record EvidenceQuery(
        String symbol,
        String naturalLanguageQuery,
        Instant cutoff,
        BrokerContext brokerContext,
        HistoricalDataRequest historicalDataRequest,
        Map<String, String> attributes) {

    public EvidenceQuery {
        symbol = required(symbol, "Symbol").toUpperCase(java.util.Locale.ROOT);
        naturalLanguageQuery = naturalLanguageQuery == null || naturalLanguageQuery.isBlank()
                ? symbol : naturalLanguageQuery.trim();
        if (cutoff == null) throw new IllegalArgumentException("Cutoff is required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static EvidenceQuery forSymbol(String symbol, String query, Instant cutoff) {
        return new EvidenceQuery(symbol, query, cutoff, null, null, Map.of());
    }

    public String attribute(String name, String fallback) {
        String value = attributes.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
