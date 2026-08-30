package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Final binary score. Diagnostic reasons never change LOSS into another label. */
public record ResolvedModelOutcome(
        String recommendationId,
        ModelOutcome outcome,
        BigDecimal netReturnAfterCosts,
        Instant resolvedAt,
        String resolutionReason,
        String marketPathEvidenceRef,
        String outcomeDefinitionVersion) {

    public ResolvedModelOutcome {
        if (recommendationId == null || recommendationId.isBlank()) throw new IllegalArgumentException("Recommendation id is required");
        recommendationId = recommendationId.trim();
        Objects.requireNonNull(outcome, "Model outcome is required");
        Objects.requireNonNull(netReturnAfterCosts, "Net return after costs is required");
        Objects.requireNonNull(resolvedAt, "Resolved-at time is required");
        resolutionReason = required(resolutionReason, "Resolution reason");
        marketPathEvidenceRef = required(marketPathEvidenceRef, "Market-path evidence reference");
        outcomeDefinitionVersion = required(outcomeDefinitionVersion, "Outcome definition version");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
