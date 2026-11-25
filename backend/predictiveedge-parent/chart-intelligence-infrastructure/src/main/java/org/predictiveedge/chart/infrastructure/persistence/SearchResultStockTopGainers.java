package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the TOP_GAINERS category. */
@Entity
@Table(name = "search_result_stock_top_gainers", schema = "market_intelligence")
public class SearchResultStockTopGainers extends SearchResultStockRow {
    protected SearchResultStockTopGainers() { }

    public SearchResultStockTopGainers(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
