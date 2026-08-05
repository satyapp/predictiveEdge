package org.predictiveedge.broker.spi;

import org.predictiveedge.broker.domain.LiveMarketDataSubscription;

/** Read-only SPI for streaming broker market data; it intentionally exposes no order operation. */
public interface LiveMarketDataProvider {
    LiveMarketDataStream connect(BrokerContext context, LiveMarketDataSubscription subscription,
            LiveMarketDataListener listener);
}
