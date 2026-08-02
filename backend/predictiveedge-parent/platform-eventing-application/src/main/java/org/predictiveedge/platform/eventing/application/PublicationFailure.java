package org.predictiveedge.platform.eventing.application;

import java.time.Instant;

/** Redacted failure evidence and the earliest permitted retry time. */
public record PublicationFailure(String errorType, Instant failedAt, Instant retryAt) {
    public PublicationFailure {
        if (errorType == null || errorType.isBlank()) {
            throw new IllegalArgumentException("Publication error type is required");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("Publication failure time is required");
        }
        if (retryAt == null || retryAt.isBefore(failedAt)) {
            throw new IllegalArgumentException("Publication retry time cannot precede failure time");
        }
    }
}
