package org.predictiveedge.decision.application;

import java.util.List;
import java.util.Objects;

public record RecommendationValidationResult(boolean accepted, List<String> reasons) {
    public RecommendationValidationResult {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "Validation reasons are required"));
        if (accepted && !reasons.isEmpty()) throw new IllegalArgumentException("Accepted result cannot contain rejection reasons");
        if (!accepted && reasons.isEmpty()) throw new IllegalArgumentException("Rejected result requires at least one reason");
    }

    public static RecommendationValidationResult pass() {
        return new RecommendationValidationResult(true, List.of());
    }

    public static RecommendationValidationResult rejected(List<String> reasons) {
        return new RecommendationValidationResult(false, reasons);
    }
}
