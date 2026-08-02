package org.predictiveedge.marketintelligence.domain;

import java.util.Locale;
import java.util.Objects;

/** Complete identity of a market-context stream. */
public record MarketContextKey(ContextScopeType scopeType, String scopeId, String horizon) {
    public MarketContextKey {
        Objects.requireNonNull(scopeType, "Context scope type is required");
        scopeId = normalize(scopeId, "Context scope id");
        horizon = normalize(horizon, "Context horizon");
    }

    private static String normalize(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
