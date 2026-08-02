package org.predictiveedge.platform.eventing.application;

/** Infrastructure-facing port for appending an already committed event to the event transport. */
@FunctionalInterface
public interface EventTransport {
    PublicationReceipt publish(EventPublication publication);
}
