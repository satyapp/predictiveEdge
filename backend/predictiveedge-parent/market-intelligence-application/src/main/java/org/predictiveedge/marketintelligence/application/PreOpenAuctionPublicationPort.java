package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface PreOpenAuctionPublicationPort {
    PreOpenAuctionSnapshot publish(
            UUID userId, String brokerAccountId, PreOpenAuctionObservation observation, Instant receivedAt);
}
