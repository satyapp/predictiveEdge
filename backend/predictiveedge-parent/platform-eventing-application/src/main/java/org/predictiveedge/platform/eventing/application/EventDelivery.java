package org.predictiveedge.platform.eventing.application;

import java.time.Instant;
import java.util.Objects;

import org.predictiveedge.platform.eventing.contract.EventEnvelope;

/** An event plus its immutable coordinates on the inbound transport. */
public record EventDelivery(
        EventEnvelope event,
        String topic,
        int partition,
        long offset,
        Instant receivedAt) {

    public EventDelivery {
        Objects.requireNonNull(event, "Delivered event is required");
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Delivery topic is required");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("Delivery partition cannot be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Delivery offset cannot be negative");
        }
        Objects.requireNonNull(receivedAt, "Delivery receipt time is required");
    }
}
