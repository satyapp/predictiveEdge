package org.predictiveedge.marketintelligence.application;

import java.util.UUID;
import org.predictiveedge.broker.domain.EquityMarketTick;

/** Stores an immutable, user-scoped view of one normalized FULL market-depth tick. */
@FunctionalInterface
public interface MarketDepthPublicationPort {
    void publish(UUID userId, String brokerAccountId, EquityMarketTick tick);

    static MarketDepthPublicationPort noop() {
        return (userId, brokerAccountId, tick) -> { };
    }
}
