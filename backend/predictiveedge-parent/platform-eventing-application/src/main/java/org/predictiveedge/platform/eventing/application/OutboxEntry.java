package org.predictiveedge.platform.eventing.application;

import java.util.Objects;
import java.util.UUID;

/** A committed event claimed for an outbox publication attempt. */
public record OutboxEntry(
        UUID outboxId,
        UUID leaseId,
        EventPublication publication,
        int priorAttemptCount) {
    public OutboxEntry {
        Objects.requireNonNull(outboxId, "Outbox id is required");
        Objects.requireNonNull(leaseId, "Outbox lease id is required");
        Objects.requireNonNull(publication, "Outbox publication is required");
        if (priorAttemptCount < 0) {
            throw new IllegalArgumentException("Prior attempt count cannot be negative");
        }
    }
}
