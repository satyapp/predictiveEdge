package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the PRE_OPEN category. */
@Entity
@Table(name = "search_result_stock_pre_open", schema = "market_intelligence")
public class SearchResultStockPreOpen extends SearchResultStockRow {
    protected SearchResultStockPreOpen() { }

    public SearchResultStockPreOpen(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
