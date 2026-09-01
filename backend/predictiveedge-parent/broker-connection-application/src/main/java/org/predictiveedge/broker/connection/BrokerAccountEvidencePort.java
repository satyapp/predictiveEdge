package org.predictiveedge.broker.connection;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;

public interface BrokerAccountEvidencePort {
    BrokerAccountEvidence publish(UUID userId, BrokerAccountSnapshot snapshot);

    Optional<BrokerAccountEvidence> latestAtOrBefore(
            UUID userId, String brokerAccountId, Instant knowledgeCutoff);
}
