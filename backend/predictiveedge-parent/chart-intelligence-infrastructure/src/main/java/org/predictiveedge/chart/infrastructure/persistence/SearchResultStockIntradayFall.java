package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the INTRADAY_FALL category. */
@Entity
@Table(name = "search_result_stock_intraday_fall", schema = "market_intelligence")
public class SearchResultStockIntradayFall extends SearchResultStockRow {
    protected SearchResultStockIntradayFall() { }

    public SearchResultStockIntradayFall(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
