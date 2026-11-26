package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the PRE_OPEN category. */
@Entity
@Table(name = "search_result_pre_open", schema = "market_intelligence")
public class SearchResultPreOpen extends SearchResultStockRow {
    protected SearchResultPreOpen() { }

    public SearchResultPreOpen(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
