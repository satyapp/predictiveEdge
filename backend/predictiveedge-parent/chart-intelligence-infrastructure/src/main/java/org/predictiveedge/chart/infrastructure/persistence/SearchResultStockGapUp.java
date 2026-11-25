package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the GAP_UP category. */
@Entity
@Table(name = "search_result_stock_gap_up", schema = "market_intelligence")
public class SearchResultStockGapUp extends SearchResultStockRow {
    protected SearchResultStockGapUp() { }

    public SearchResultStockGapUp(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
