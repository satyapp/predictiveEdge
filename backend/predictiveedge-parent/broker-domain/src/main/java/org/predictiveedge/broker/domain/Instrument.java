package org.predictiveedge.broker.domain;

public record Instrument(String exchange, String symbol) {
    public Instrument {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange is required");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        exchange = exchange.trim().toUpperCase();
        symbol = symbol.trim().toUpperCase();
    }
}
