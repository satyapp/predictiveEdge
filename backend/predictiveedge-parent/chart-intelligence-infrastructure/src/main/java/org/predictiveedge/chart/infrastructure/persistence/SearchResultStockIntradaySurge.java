package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the INTRADAY_SURGE category. */
@Entity
@Table(name = "search_result_stock_intraday_surge", schema = "market_intelligence")
public class SearchResultStockIntradaySurge extends SearchResultStockRow {
    protected SearchResultStockIntradaySurge() { }

    public SearchResultStockIntradaySurge(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
