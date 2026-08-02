package org.predictiveedge.marketintelligence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Versioned event-time policy that determines when a completed bar may become final. */
public record BarFinalityPolicy(Duration allowedLateness, String version) {
    public BarFinalityPolicy {
        Objects.requireNonNull(allowedLateness, "Allowed lateness is required");
        if (allowedLateness.isNegative()) {
            throw new IllegalArgumentException("Allowed lateness cannot be negative");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Finality policy version is required");
        }
        version = version.trim();
    }

    public Instant finalityReadyAt(BarInterval interval) {
        Objects.requireNonNull(interval, "Bar interval is required");
        return interval.endsAt().plus(allowedLateness);
    }

    public boolean canFinalize(BarInterval interval, Instant eventTimeWatermark) {
        Objects.requireNonNull(eventTimeWatermark, "Event-time watermark is required");
        return !eventTimeWatermark.isBefore(finalityReadyAt(interval));
    }
}
