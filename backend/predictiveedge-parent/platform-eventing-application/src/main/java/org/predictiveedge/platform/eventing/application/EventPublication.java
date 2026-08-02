package org.predictiveedge.platform.eventing.application;

import java.util.Objects;

import org.predictiveedge.platform.eventing.contract.EventEnvelope;

/** A domain event paired with its bounded-context-owned destination topic. */
public record EventPublication(String destinationTopic, EventEnvelope event) {
    public EventPublication {
        if (destinationTopic == null
                || destinationTopic.length() > 249
                || !destinationTopic.matches("[a-zA-Z0-9._-]+")
                || destinationTopic.equals(".")
                || destinationTopic.equals("..")) {
            throw new IllegalArgumentException("Destination topic is invalid");
        }
        Objects.requireNonNull(event, "Published event is required");
    }
}
