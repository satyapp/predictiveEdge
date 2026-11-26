package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for PRE_OPEN_ANALYSIS, one row per selection, stage and source. */
@Entity
@Table(name = "stock_evidence_pre_open", schema = "market_intelligence")
public class StockEvidencePreOpen extends EvidenceRow {
    protected StockEvidencePreOpen() { }

    public StockEvidencePreOpen(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
