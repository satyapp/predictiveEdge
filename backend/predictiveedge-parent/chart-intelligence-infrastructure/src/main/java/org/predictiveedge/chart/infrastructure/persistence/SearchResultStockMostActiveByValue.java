package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the MOST_ACTIVE_BY_VALUE category. */
@Entity
@Table(name = "search_result_stock_most_active_by_value", schema = "market_intelligence")
public class SearchResultStockMostActiveByValue extends SearchResultStockRow {
    protected SearchResultStockMostActiveByValue() { }

    public SearchResultStockMostActiveByValue(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
