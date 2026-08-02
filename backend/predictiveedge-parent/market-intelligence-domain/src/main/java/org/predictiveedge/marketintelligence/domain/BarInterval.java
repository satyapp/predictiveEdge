package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Objects;

/** End-exclusive interval aligned to the session's canonical bar anchor. */
public record BarInterval(Instant startsAt, Instant endsAt, boolean truncatedBySessionEnd) {
    public BarInterval {
        Objects.requireNonNull(startsAt, "Bar start is required");
        Objects.requireNonNull(endsAt, "Bar end is required");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Bar start must precede bar end");
        }
    }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "Instant is required");
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
