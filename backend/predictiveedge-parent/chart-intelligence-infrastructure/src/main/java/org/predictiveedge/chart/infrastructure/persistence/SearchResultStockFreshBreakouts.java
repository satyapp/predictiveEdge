package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the FRESH_BREAKOUTS category. */
@Entity
@Table(name = "search_result_stock_fresh_breakouts", schema = "market_intelligence")
public class SearchResultStockFreshBreakouts extends SearchResultStockRow {
    protected SearchResultStockFreshBreakouts() { }

    public SearchResultStockFreshBreakouts(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
