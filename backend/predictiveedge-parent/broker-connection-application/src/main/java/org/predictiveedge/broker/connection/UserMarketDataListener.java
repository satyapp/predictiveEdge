package org.predictiveedge.broker.connection;

import java.util.List;
import java.util.UUID;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;

/** User-aware sink for normalized broker market-data events. */
public interface UserMarketDataListener {
    void onTicks(UUID userId, String brokerAccountId, List<MarketTick> ticks);
    void onStateChanged(UUID userId, String brokerAccountId, MarketDataStreamState state);
    void onFailure(UUID userId, String brokerAccountId, RuntimeException failure);
}
