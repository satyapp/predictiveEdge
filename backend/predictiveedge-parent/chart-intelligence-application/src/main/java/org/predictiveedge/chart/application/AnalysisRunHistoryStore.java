package org.predictiveedge.chart.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** User-scoped, append-only persistence for completed analysis reports. */
public interface AnalysisRunHistoryStore {
    void append(StoredAnalysisRun run);

    List<AnalysisRunSummary> findRecent(UUID userId, int limit);

    Optional<StoredAnalysisRun> findById(UUID userId, UUID runId);

    record StoredAnalysisRun(UUID runId, UUID userId, String category, String resultKind,
            String fileName, String sourceSha256, LocalDate tradingDate, int candidateCount,
            String stageOneModel, Instant createdAt, String resultJson) { }

    record AnalysisRunSummary(UUID runId, String category, String resultKind, String fileName,
            String sourceSha256, LocalDate tradingDate, int candidateCount, String stageOneModel,
            Instant createdAt) { }
}
