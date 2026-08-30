package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Causal replay result calculated from executable prices and frozen cost rules. */
public record OutcomeObservation(
        boolean entryBecameValid,
        boolean stopReachedFirst,
        BigDecimal netReturnAfterCosts,
        Instant resolvedAt,
        String marketPathEvidenceRef) {

    public OutcomeObservation {
        Objects.requireNonNull(netReturnAfterCosts, "Net return after costs is required");
        Objects.requireNonNull(resolvedAt, "Resolved-at time is required");
        if (marketPathEvidenceRef == null || marketPathEvidenceRef.isBlank()) {
            throw new IllegalArgumentException("Market-path evidence reference is required");
        }
        marketPathEvidenceRef = marketPathEvidenceRef.trim();
    }
}
