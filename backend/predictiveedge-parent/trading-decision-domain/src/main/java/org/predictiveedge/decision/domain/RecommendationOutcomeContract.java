package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Frozen rules used to resolve a directional AI recommendation without hindsight. */
public record RecommendationOutcomeContract(
        String recommendationId,
        RecommendationAction direction,
        BigDecimal entryPriceLow,
        BigDecimal entryPriceHigh,
        Instant entryValidUntil,
        Instant evaluationHorizon,
        BigDecimal stopLoss,
        BigDecimal target,
        int normalizedQuantity,
        String executionCostModelVersion,
        String outcomeDefinitionVersion) {

    public RecommendationOutcomeContract {
        if (recommendationId == null || recommendationId.isBlank()) throw new IllegalArgumentException("Recommendation id is required");
        recommendationId = recommendationId.trim();
        Objects.requireNonNull(direction, "Direction is required");
        if (!direction.isDirectional()) throw new IllegalArgumentException("Outcome contract requires BUY or SELL");
        Objects.requireNonNull(entryPriceLow, "Entry price low is required");
        Objects.requireNonNull(entryPriceHigh, "Entry price high is required");
        if (entryPriceLow.signum() <= 0 || entryPriceHigh.compareTo(entryPriceLow) < 0) {
            throw new IllegalArgumentException("Entry range is invalid");
        }
        Objects.requireNonNull(entryValidUntil, "Entry-valid-until time is required");
        Objects.requireNonNull(evaluationHorizon, "Evaluation horizon is required");
        if (!entryValidUntil.isBefore(evaluationHorizon)) throw new IllegalArgumentException("Outcome horizon is invalid");
        Objects.requireNonNull(stopLoss, "Stop loss is required");
        Objects.requireNonNull(target, "Target is required");
        if (normalizedQuantity <= 0) throw new IllegalArgumentException("Normalized quantity must be positive");
        executionCostModelVersion = required(executionCostModelVersion, "Execution cost model version");
        outcomeDefinitionVersion = required(outcomeDefinitionVersion, "Outcome definition version");
    }

    public static RecommendationOutcomeContract from(AIRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "AI recommendation is required");
        return new RecommendationOutcomeContract(recommendation.recommendationId(), recommendation.action(),
                recommendation.entryPriceLow(), recommendation.entryPriceHigh(), recommendation.entryValidUntil(),
                recommendation.evaluationHorizon(), recommendation.stopLoss(), recommendation.target(), 1,
                "shadow-cost-v1", "strict-binary-v1");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
