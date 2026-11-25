package org.predictiveedge.chart.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Persists evidence for the stocks sent to AI analysis, keyed by search run and category. */
public interface SearchEvidenceStore {
    /** Writes the run and all of its stock rows in one transaction. */
    void save(SearchRun run, List<StockEvidence> stocks);

    record SearchRun(UUID searchId, UUID userId, SearchEvidenceCategory category, String screenType,
            String setup, LocalDate tradingDate, Instant evidenceCutoff, List<String> requestedSymbols,
            Instant createdAt) {
        public SearchRun {
            requestedSymbols = List.copyOf(requestedSymbols);
        }
    }

    /** JSON strings are stored as jsonb without interpretation. */
    record StockEvidence(String symbol, String evidenceByDomainJson, String intelligenceModulesJson) { }
}
