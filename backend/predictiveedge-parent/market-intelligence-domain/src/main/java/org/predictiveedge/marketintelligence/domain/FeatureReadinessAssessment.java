package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Explainable readiness result; warming-up and unavailable are not numeric values. */
public record FeatureReadinessAssessment(
        FeatureReadiness readiness,
        int availableBars,
        int requiredBars,
        String reason) {

    public FeatureReadinessAssessment {
        Objects.requireNonNull(readiness, "Feature readiness is required");
        if (availableBars < 0 || requiredBars < 1) {
            throw new IllegalArgumentException("Feature bar counts are invalid");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Readiness reason is required");
        }
        reason = reason.trim();
    }
}
