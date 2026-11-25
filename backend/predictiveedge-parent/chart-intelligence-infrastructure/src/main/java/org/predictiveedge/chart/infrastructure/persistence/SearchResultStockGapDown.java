package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the GAP_DOWN category. */
@Entity
@Table(name = "search_result_stock_gap_down", schema = "market_intelligence")
public class SearchResultStockGapDown extends SearchResultStockRow {
    protected SearchResultStockGapDown() { }

    public SearchResultStockGapDown(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
