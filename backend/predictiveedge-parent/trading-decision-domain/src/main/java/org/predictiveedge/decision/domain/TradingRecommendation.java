package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Final advisory result with exact intelligence inputs; it is never an executable order. */
public record TradingRecommendation(
        String recommendationId,
        String traderIntentId,
        InstrumentRef instrument,
        RecommendationAction action,
        int confidence,
        Instant evaluatedAt,
        DecisionReason primaryReason,
        List<IntelligenceModule> blockingModules,
        List<String> feedbackReferences,
        String evidenceManifestHash) {

    public TradingRecommendation {
        recommendationId = required(recommendationId, "Recommendation id");
        traderIntentId = required(traderIntentId, "Trader intent id");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(action, "Action is required");
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence must be 0-100");
        if (!action.isDirectional() && confidence != 0) {
            throw new IllegalArgumentException("Non-directional outcomes must have zero confidence");
        }
        Objects.requireNonNull(evaluatedAt, "Evaluation time is required");
        Objects.requireNonNull(primaryReason, "Decision reason is required");
        blockingModules = List.copyOf(Objects.requireNonNull(blockingModules, "Blocking modules are required"));
        feedbackReferences = List.copyOf(Objects.requireNonNull(feedbackReferences, "Feedback references are required"));
        evidenceManifestHash = required(evidenceManifestHash, "Evidence manifest hash");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
