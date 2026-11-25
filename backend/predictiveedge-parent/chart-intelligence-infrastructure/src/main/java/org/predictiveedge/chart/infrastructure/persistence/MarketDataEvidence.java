package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for MARKET_DATA, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_market_data", schema = "market_intelligence")
public class MarketDataEvidence extends EvidenceRow {
    protected MarketDataEvidence() { }

    public MarketDataEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
