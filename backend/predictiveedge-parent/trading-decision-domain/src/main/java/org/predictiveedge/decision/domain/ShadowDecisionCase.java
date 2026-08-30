package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Append-only shadow evaluation. It has no execution state and cannot reach a broker. */
public record ShadowDecisionCase(
        String caseId,
        AITradingDecisionInputBundle inputBundle,
        AIRecommendation recommendation,
        ShadowCaseStatus status,
        List<String> policyReasons,
        Instant recordedAt) {

    public ShadowDecisionCase {
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("Case id is required");
        caseId = caseId.trim();
        Objects.requireNonNull(inputBundle, "Input bundle is required");
        Objects.requireNonNull(status, "Case status is required");
        policyReasons = List.copyOf(Objects.requireNonNull(policyReasons, "Policy reasons are required"));
        if (policyReasons.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Policy reasons cannot contain blanks");
        }
        Objects.requireNonNull(recordedAt, "Recorded-at time is required");
        if (status == ShadowCaseStatus.BLOCKED_INPUT && recommendation != null) {
            throw new IllegalArgumentException("Blocked input case cannot contain an AI recommendation");
        }
        if (status != ShadowCaseStatus.BLOCKED_INPUT && recommendation == null) {
            throw new IllegalArgumentException("Evaluated case requires an AI recommendation");
        }
        if (recommendation != null) {
            if (!recommendation.bundleId().equals(inputBundle.bundleId())) {
                throw new IllegalArgumentException("Recommendation must reference the stored bundle");
            }
            if (!recommendation.scope().equals(inputBundle.scope())) {
                throw new IllegalArgumentException("Recommendation scope must match the stored bundle");
            }
        }
    }
}
