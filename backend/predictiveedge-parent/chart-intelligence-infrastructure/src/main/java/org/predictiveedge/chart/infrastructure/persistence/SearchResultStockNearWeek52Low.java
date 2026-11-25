package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the NEAR_WEEK_52_LOW category. */
@Entity
@Table(name = "search_result_stock_near_week_52_low", schema = "market_intelligence")
public class SearchResultStockNearWeek52Low extends SearchResultStockRow {
    protected SearchResultStockNearWeek52Low() { }

    public SearchResultStockNearWeek52Low(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
