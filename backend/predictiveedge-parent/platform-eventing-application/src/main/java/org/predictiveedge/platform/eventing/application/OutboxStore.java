package org.predictiveedge.platform.eventing.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable storage port for claiming and completing transactional outbox records. */
public interface OutboxStore {
    List<OutboxEntry> claimPending(int batchSize, Instant claimedAt, Duration leaseDuration);

    void markPublished(UUID outboxId, UUID leaseId, PublicationReceipt receipt);

    void markFailed(UUID outboxId, UUID leaseId, PublicationFailure failure);
}
