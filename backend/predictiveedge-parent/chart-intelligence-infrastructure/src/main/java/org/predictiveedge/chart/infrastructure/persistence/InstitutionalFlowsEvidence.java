package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for INSTITUTIONAL_FLOWS, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_institutional_flows", schema = "market_intelligence")
public class InstitutionalFlowsEvidence extends EvidenceRow {
    protected InstitutionalFlowsEvidence() { }

    public InstitutionalFlowsEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
