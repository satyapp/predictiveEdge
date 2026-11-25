package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for MARKET_DEPTH, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_market_depth", schema = "market_intelligence")
public class MarketDepthEvidence extends EvidenceRow {
    protected MarketDepthEvidence() { }

    public MarketDepthEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
