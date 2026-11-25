package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the TOP_LOSERS category. */
@Entity
@Table(name = "search_result_stock_top_losers", schema = "market_intelligence")
public class SearchResultStockTopLosers extends SearchResultStockRow {
    protected SearchResultStockTopLosers() { }

    public SearchResultStockTopLosers(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
