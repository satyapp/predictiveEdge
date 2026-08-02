package org.predictiveedge.platform.eventing.application;

/**
 * Domain-facing port that stages an event in the same transaction as its domain change.
 * Implementations must not publish directly to Kafka from the caller's transaction.
 */
@FunctionalInterface
public interface DomainEventPublisher {
    void stage(EventPublication publication);
}
