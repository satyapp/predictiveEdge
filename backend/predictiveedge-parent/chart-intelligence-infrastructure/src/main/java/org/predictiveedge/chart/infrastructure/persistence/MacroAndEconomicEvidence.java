package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for MACRO_AND_ECONOMIC, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_macro_and_economic", schema = "market_intelligence")
public class MacroAndEconomicEvidence extends EvidenceRow {
    protected MacroAndEconomicEvidence() { }

    public MacroAndEconomicEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
