package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for HISTORICAL_AND_PREDICTIVE, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_historical_and_predictive", schema = "market_intelligence")
public class HistoricalAndPredictiveEvidence extends EvidenceRow {
    protected HistoricalAndPredictiveEvidence() { }

    public HistoricalAndPredictiveEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
