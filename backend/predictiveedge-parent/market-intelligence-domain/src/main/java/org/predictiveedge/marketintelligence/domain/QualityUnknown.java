package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Explicit evidence gap; absence is never represented as zero or neutral. */
public record QualityUnknown(QualityIssueCode cause, String affectedComponent, String reason) {
    public QualityUnknown {
        Objects.requireNonNull(cause, "Unknown-evidence cause is required");
        if (affectedComponent == null || affectedComponent.isBlank()) {
            throw new IllegalArgumentException("Unknown affected component is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Unknown reason is required");
        }
        affectedComponent = affectedComponent.trim();
        reason = reason.trim();
    }
}
