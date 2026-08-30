package org.predictiveedge.decision.domain;

import java.util.Objects;

/** Applies the strict binary outcome policy to a frozen contract and causal replay observation. */
public final class RecommendationOutcomeResolver {
    public ResolvedModelOutcome resolve(RecommendationOutcomeContract contract, OutcomeObservation observation) {
        Objects.requireNonNull(contract, "Outcome contract is required");
        Objects.requireNonNull(observation, "Outcome observation is required");
        if (observation.resolvedAt().isBefore(contract.entryValidUntil())) {
            throw new IllegalArgumentException("Outcome cannot resolve before the entry window closes");
        }
        boolean win = observation.entryBecameValid()
                && !observation.stopReachedFirst()
                && observation.netReturnAfterCosts().signum() > 0;
        String reason = !observation.entryBecameValid() ? "ENTRY_NOT_VALID"
                : observation.stopReachedFirst() ? "STOP_REACHED_FIRST"
                : observation.netReturnAfterCosts().signum() > 0 ? "POSITIVE_AFTER_COSTS"
                : "ZERO_OR_NEGATIVE_AFTER_COSTS";
        return new ResolvedModelOutcome(contract.recommendationId(), win ? ModelOutcome.WIN : ModelOutcome.LOSS,
                observation.netReturnAfterCosts(), observation.resolvedAt(), reason,
                observation.marketPathEvidenceRef(), contract.outcomeDefinitionVersion());
    }
}
