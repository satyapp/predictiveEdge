package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, tenant-owned pre-open auction evidence with point-in-time availability. */
public record PreOpenAuctionSnapshot(
        UUID snapshotId,
        UUID userId,
        String brokerAccountId,
        PreOpenAuctionObservation observation,
        Instant receivedAt,
        String evidenceHash) {

    public PreOpenAuctionSnapshot {
        Objects.requireNonNull(snapshotId, "Snapshot id is required");
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        brokerAccountId = brokerAccountId.trim();
        Objects.requireNonNull(observation, "Observation is required");
        Objects.requireNonNull(receivedAt, "Receipt timestamp is required");
        if (receivedAt.isBefore(observation.observedAt()))
            throw new IllegalArgumentException("Receipt timestamp cannot precede observation timestamp");
        if (evidenceHash == null || !evidenceHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Evidence hash must be lowercase SHA-256");
    }
}
