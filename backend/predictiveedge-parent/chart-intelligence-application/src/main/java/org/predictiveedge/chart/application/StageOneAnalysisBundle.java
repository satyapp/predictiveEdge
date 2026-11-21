package org.predictiveedge.chart.application;

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
        StageOneGoldenCopy goldenCopy) {

    public StageOneAnalysisBundle {
        Objects.requireNonNull(analysisId, "Analysis id is required");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(goldenCopy, "Stage-1 golden copy is required");
    }

    public String category() { return goldenCopy.category(); }
    public String sourceFingerprint() { return goldenCopy.sourceFingerprint(); }
    public java.time.Instant frozenAt() { return goldenCopy.frozenAt(); }
    public String modelId() { return goldenCopy.stageOneAnalysis().model(); }
    public java.time.Instant generatedAt() { return goldenCopy.stageOneAnalysis().generatedAt(); }
    public String evidenceJson() { return goldenCopy.intelligenceEvidenceJson(); }
    public PreOpenOllamaAnalysis stageOneAnalysis() { return goldenCopy.stageOneAnalysis(); }
}
