package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.domain.Instrument;

public interface MarketDepthQueryPort {
    Optional<MarketDepthSnapshot> latestAtOrBefore(
            UUID userId, String brokerAccountId, Instrument instrument, Instant knowledgeCutoff);
}
