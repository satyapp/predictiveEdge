package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable hand-off from deterministic screening plus Ollama to an optional advanced model.
 *
 * <p>The serialized evidence is captured before Stage 2. Advanced model adapters receive this
 * bundle only; they must not depend on broker or market-data connector ports.</p>
 */
public record StageOneAnalysisBundle(
        UUID analysisId,
        UUID userId,
        String category,
        String sourceFingerprint,
        Instant frozenAt,
        String modelId,
        Instant generatedAt,
        String evidenceJson) {

    public StageOneAnalysisBundle {
        Objects.requireNonNull(analysisId, "Analysis id is required");
        Objects.requireNonNull(userId, "User id is required");
        category = required(category, "Category").toUpperCase(Locale.ROOT);
        sourceFingerprint = required(sourceFingerprint, "Source fingerprint");
        Objects.requireNonNull(frozenAt, "Frozen-at time is required");
        modelId = required(modelId, "Stage-1 model id");
        Objects.requireNonNull(generatedAt, "Stage-1 generated-at time is required");
        evidenceJson = required(evidenceJson, "Evidence JSON");
        if (generatedAt.isAfter(frozenAt))
            throw new IllegalArgumentException("Stage-1 generation cannot follow evidence freeze time");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
