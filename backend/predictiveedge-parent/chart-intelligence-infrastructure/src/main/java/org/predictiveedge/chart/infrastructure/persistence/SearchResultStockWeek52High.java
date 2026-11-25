package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the WEEK_52_HIGH category. */
@Entity
@Table(name = "search_result_stock_week_52_high", schema = "market_intelligence")
public class SearchResultStockWeek52High extends SearchResultStockRow {
    protected SearchResultStockWeek52High() { }

    public SearchResultStockWeek52High(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
