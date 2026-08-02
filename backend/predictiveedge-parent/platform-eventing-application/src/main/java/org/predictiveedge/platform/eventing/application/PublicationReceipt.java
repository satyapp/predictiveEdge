package org.predictiveedge.platform.eventing.application;

import java.time.Instant;
import java.util.Objects;

/** Broker coordinates retained after successful publication. */
public record PublicationReceipt(String topic, int partition, long offset, Instant publishedAt) {
    public PublicationReceipt {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Publication topic is required");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("Publication partition cannot be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Publication offset cannot be negative");
        }
        Objects.requireNonNull(publishedAt, "Publication time is required");
    }
}
