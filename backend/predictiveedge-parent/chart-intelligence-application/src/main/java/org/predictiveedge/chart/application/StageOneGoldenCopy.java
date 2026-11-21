package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.Objects;

/** Immutable, persisted hand-off containing every Stage-1 input and the complete Stage-1 output. */
public record StageOneGoldenCopy(
        String schemaVersion,
        String category,
        Instant frozenAt,
        String sourceFingerprint,
        String intelligenceEvidenceJson,
        PreOpenOllamaAnalysis stageOneAnalysis,
        String stageTwoStatus) {

    public StageOneGoldenCopy {
        schemaVersion = required(schemaVersion, "Schema version");
        category = required(category, "Category");
        Objects.requireNonNull(frozenAt, "Frozen-at timestamp is required");
        sourceFingerprint = required(sourceFingerprint, "Source fingerprint");
        intelligenceEvidenceJson = required(intelligenceEvidenceJson, "Intelligence evidence JSON");
        Objects.requireNonNull(stageOneAnalysis, "Stage-1 analysis is required");
        stageTwoStatus = required(stageTwoStatus, "Stage-2 status");
        if (stageOneAnalysis.generatedAt().isAfter(frozenAt))
            throw new IllegalArgumentException("Golden copy cannot freeze before Stage-1 generation");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
