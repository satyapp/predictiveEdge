package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for FUNDAMENTAL_ANALYSIS, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_fundamental_analysis", schema = "market_intelligence")
public class FundamentalAnalysisEvidence extends EvidenceRow {
    protected FundamentalAnalysisEvidence() { }

    public FundamentalAnalysisEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
