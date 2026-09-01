package org.predictiveedge.broker.connection;

import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;

public record BrokerAccountEvidence(UUID snapshotId, BrokerAccountSnapshot snapshot, String evidenceHash) {
    public BrokerAccountEvidence {
        Objects.requireNonNull(snapshotId, "Snapshot id is required");
        Objects.requireNonNull(snapshot, "Broker account snapshot is required");
        if (evidenceHash == null || !evidenceHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Evidence hash must be lowercase SHA-256");
    }
}
