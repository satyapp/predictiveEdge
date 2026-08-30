package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Structured advisory response returned by the AI gateway; never an executable broker order. */
public record AIRecommendation(
        String recommendationId,
        String bundleId,
        ShadowScope scope,
        RecommendationAction action,
        Instant generatedAt,
        BigDecimal calibratedWinProbability,
        BigDecimal expectedValueAfterCosts,
        BigDecimal entryPriceLow,
        BigDecimal entryPriceHigh,
        Instant entryValidUntil,
        Instant evaluationHorizon,
        BigDecimal stopLoss,
        BigDecimal target,
        String modelId,
        String rationale,
        List<String> evidenceReferences) {

    public AIRecommendation {
        recommendationId = required(recommendationId, "Recommendation id");
        bundleId = required(bundleId, "Bundle id");
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(action, "Recommendation action is required");
        Objects.requireNonNull(generatedAt, "Generated-at time is required");
        probability(calibratedWinProbability);
        Objects.requireNonNull(expectedValueAfterCosts, "Expected value after costs is required");
        modelId = required(modelId, "Model id");
        rationale = required(rationale, "Rationale");
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
        if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blanks");
        }
        if (action.isDirectional()) validateDirectionalPlan(action, generatedAt, entryPriceLow, entryPriceHigh,
                entryValidUntil, evaluationHorizon, stopLoss, target);
    }

    private static void validateDirectionalPlan(RecommendationAction action, Instant generatedAt,
            BigDecimal entryPriceLow, BigDecimal entryPriceHigh, Instant entryValidUntil,
            Instant evaluationHorizon, BigDecimal stopLoss, BigDecimal target) {
        positive(entryPriceLow, "Entry price low");
        positive(entryPriceHigh, "Entry price high");
        if (entryPriceHigh.compareTo(entryPriceLow) < 0) throw new IllegalArgumentException("Entry price range is invalid");
        Objects.requireNonNull(entryValidUntil, "Entry-valid-until time is required");
        Objects.requireNonNull(evaluationHorizon, "Evaluation horizon is required");
        if (!generatedAt.isBefore(entryValidUntil) || !entryValidUntil.isBefore(evaluationHorizon)) {
            throw new IllegalArgumentException("Recommendation time horizon is invalid");
        }
        positive(stopLoss, "Stop loss");
        positive(target, "Target");
        if (action == RecommendationAction.BUY && (stopLoss.compareTo(entryPriceLow) >= 0
                || target.compareTo(entryPriceHigh) <= 0)) {
            throw new IllegalArgumentException("BUY stop/target must surround the entry range");
        }
        if (action == RecommendationAction.SELL && (stopLoss.compareTo(entryPriceHigh) <= 0
                || target.compareTo(entryPriceLow) >= 0)) {
            throw new IllegalArgumentException("SELL stop/target must surround the entry range");
        }
    }

    private static void probability(BigDecimal value) {
        Objects.requireNonNull(value, "Calibrated win probability is required");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Calibrated win probability must be between zero and one");
        }
    }

    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
