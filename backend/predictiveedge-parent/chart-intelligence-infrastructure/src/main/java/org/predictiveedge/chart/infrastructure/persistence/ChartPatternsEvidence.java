package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for CHART_PATTERNS, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_chart_patterns", schema = "market_intelligence")
public class ChartPatternsEvidence extends EvidenceRow {
    protected ChartPatternsEvidence() { }

    public ChartPatternsEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
