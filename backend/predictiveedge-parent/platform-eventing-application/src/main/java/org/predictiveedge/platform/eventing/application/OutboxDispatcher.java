package org.predictiveedge.platform.eventing.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Publishes bounded batches of committed outbox events with at-least-once semantics. */
public final class OutboxDispatcher {
    private final OutboxStore store;
    private final EventTransport transport;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryDelay;

    public OutboxDispatcher(
            OutboxStore store,
            EventTransport transport,
            Clock clock,
            Duration leaseDuration,
            Duration retryDelay) {
        this.store = Objects.requireNonNull(store, "Outbox store is required");
        this.transport = Objects.requireNonNull(transport, "Event transport is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.leaseDuration = positive(leaseDuration, "Lease duration");
        this.retryDelay = positive(retryDelay, "Retry delay");
    }

    public DispatchSummary dispatch(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Dispatch batch size must be positive");
        }
        Instant claimedAt = clock.instant();
        List<OutboxEntry> entries = List.copyOf(
                store.claimPending(batchSize, claimedAt, leaseDuration));
        int published = 0;
        for (OutboxEntry entry : entries) {
            try {
                PublicationReceipt receipt = Objects.requireNonNull(
                        transport.publish(entry.publication()), "Event transport returned no receipt");
                store.markPublished(entry.outboxId(), entry.leaseId(), receipt);
                published++;
            } catch (RuntimeException failure) {
                Instant failedAt = clock.instant();
                store.markFailed(entry.outboxId(), entry.leaseId(), new PublicationFailure(
                        failure.getClass().getSimpleName(), failedAt, failedAt.plus(retryDelay)));
            }
        }
        return new DispatchSummary(entries.size(), published, entries.size() - published);
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
