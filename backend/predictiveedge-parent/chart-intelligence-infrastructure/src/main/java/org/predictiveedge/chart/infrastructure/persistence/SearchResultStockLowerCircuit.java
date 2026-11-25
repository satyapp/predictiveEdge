package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every screener row returned by a search in the LOWER_CIRCUIT category. */
@Entity
@Table(name = "search_result_stock_lower_circuit", schema = "market_intelligence")
public class SearchResultStockLowerCircuit extends SearchResultStockRow {
    protected SearchResultStockLowerCircuit() { }

    public SearchResultStockLowerCircuit(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        super(key, screenRank, brokerRow, createdAt);
    }
}
