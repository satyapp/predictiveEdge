package org.predictiveedge.marketintelligence.application;

import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;

/** Persists or publishes immutable canonical market-bar revisions. */
public interface MarketBarPublicationPort {
    void publish(UUID userId, String brokerAccountId, MarketBarRevision revision);
}
