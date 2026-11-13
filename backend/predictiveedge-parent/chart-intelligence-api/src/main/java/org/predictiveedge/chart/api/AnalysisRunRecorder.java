package org.predictiveedge.chart.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class AnalysisRunRecorder {
    private final AnalysisRunHistoryStore store;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AnalysisRunRecorder(AnalysisRunHistoryStore store, ObjectMapper json) {
        this(store, json, Clock.systemUTC());
    }

    AnalysisRunRecorder(AnalysisRunHistoryStore store, ObjectMapper json, Clock clock) {
        this.store = store;
        this.json = json;
        this.clock = clock;
    }

    public UUID record(UUID userId, String category, String resultKind, String fileName, String csv,
            LocalDate tradingDate, int candidateCount, String stageOneModel, Object result) {
        UUID runId = UUID.randomUUID();
        try {
            store.append(new AnalysisRunHistoryStore.StoredAnalysisRun(runId, userId, category, resultKind,
                    safeFileName(fileName), sha256(csv), tradingDate, candidateCount, stageOneModel,
                    clock.instant(), json.writeValueAsString(result)));
            return runId;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Completed analysis could not be persisted", failure);
        }
    }

    public List<AnalysisRunHistoryStore.AnalysisRunSummary> recent(UUID userId, int limit) {
        return store.findRecent(userId, limit);
    }

    public AnalysisRunDetail find(UUID userId, UUID runId) {
        var run = store.findById(userId, runId)
                .orElseThrow(() -> new AnalysisRunNotFoundException(runId));
        try {
            return new AnalysisRunDetail(run.runId(), run.category(), run.resultKind(), run.fileName(),
                    run.sourceSha256(), run.tradingDate(), run.candidateCount(), run.stageOneModel(),
                    run.createdAt(), json.readTree(run.resultJson()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored analysis report is invalid", failure);
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "uploaded-market-data.csv";
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return name.isEmpty() ? "uploaded-market-data.csv" : name.substring(0, Math.min(name.length(), 255));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record AnalysisRunDetail(UUID runId, String category, String resultKind, String fileName,
            String sourceSha256, LocalDate tradingDate, int candidateCount, String stageOneModel,
            Instant createdAt, JsonNode result) { }

    static final class AnalysisRunNotFoundException extends RuntimeException {
        AnalysisRunNotFoundException(UUID runId) { super("Analysis run was not found: " + runId); }
    }
}
