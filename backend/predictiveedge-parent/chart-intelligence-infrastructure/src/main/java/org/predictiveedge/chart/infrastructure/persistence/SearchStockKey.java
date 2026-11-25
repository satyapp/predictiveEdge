package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies one screener row: the search run and the stock symbol. */
@Embeddable
public class SearchStockKey implements Serializable {
    @Column(name = "search_id", nullable = false)
    private UUID searchId;
    @Column(name = "symbol", nullable = false)
    private String symbol;

    protected SearchStockKey() { }

    public SearchStockKey(UUID searchId, String symbol) {
        this.searchId = Objects.requireNonNull(searchId, "searchId is required");
        this.symbol = Objects.requireNonNull(symbol, "symbol is required");
    }

    public UUID searchId() { return searchId; }
    public String symbol() { return symbol; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SearchStockKey other)) return false;
        return Objects.equals(searchId, other.searchId) && Objects.equals(symbol, other.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchId, symbol);
    }
}
