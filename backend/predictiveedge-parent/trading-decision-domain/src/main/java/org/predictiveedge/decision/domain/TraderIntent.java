package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Explicit human authorization boundary within which an advisory recommendation may be produced. */
public record TraderIntent(
        String intentId,
        UUID traderId,
        InstrumentRef instrument,
        Set<RecommendationAction> permittedActions,
        String strategyProfile,
        Instant validFrom,
        Instant validUntil) {

    public TraderIntent {
        intentId = required(intentId, "Trader intent id");
        Objects.requireNonNull(traderId, "Trader id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(permittedActions, "Permitted actions are required");
        EnumSet<RecommendationAction> actions = permittedActions.isEmpty()
                ? EnumSet.noneOf(RecommendationAction.class)
                : EnumSet.copyOf(permittedActions);
        if (actions.isEmpty() || actions.stream().anyMatch(action -> !action.isDirectional())) {
            throw new IllegalArgumentException("Trader intent must permit BUY, SELL, or both");
        }
        permittedActions = Collections.unmodifiableSet(actions);
        strategyProfile = required(strategyProfile, "Strategy profile");
        Objects.requireNonNull(validFrom, "Valid-from time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (!validFrom.isBefore(validUntil)) throw new IllegalArgumentException("Trader intent validity is invalid");
    }

    public boolean isActiveAt(Instant time) {
        Objects.requireNonNull(time, "Evaluation time is required");
        return !time.isBefore(validFrom) && time.isBefore(validUntil);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
