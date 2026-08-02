package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Objects;

/** End-exclusive time window in a versioned market session calendar. */
public record SessionPhaseWindow(MarketSessionPhase phase, Instant startsAt, Instant endsAt) {
    public SessionPhaseWindow {
        Objects.requireNonNull(phase, "Session phase is required");
        Objects.requireNonNull(startsAt, "Phase start is required");
        Objects.requireNonNull(endsAt, "Phase end is required");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Phase start must precede phase end");
        }
    }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "Instant is required");
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
