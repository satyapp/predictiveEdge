package org.predictiveedge.broker.spi;

import java.util.List;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;

/** Non-blocking sink for normalized market-data events. Implementations must preserve callback order. */
public interface LiveMarketDataListener {
    void onTicks(List<MarketTick> ticks);
    void onStateChanged(MarketDataStreamState state);
    void onFailure(RuntimeException failure);
}
