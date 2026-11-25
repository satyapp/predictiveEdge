package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for CORPORATE_ACTIONS, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_corporate_actions", schema = "market_intelligence")
public class CorporateActionsEvidence extends EvidenceRow {
    protected CorporateActionsEvidence() { }

    public CorporateActionsEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
