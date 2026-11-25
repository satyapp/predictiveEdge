package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the VOLUME_GAINERS category. */
@Entity
@Table(name = "search_result_stock_volume_gainers", schema = "market_intelligence")
public class SearchResultStockVolumeGainers extends SearchResultStockRow {
    protected SearchResultStockVolumeGainers() { }

    public SearchResultStockVolumeGainers(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
