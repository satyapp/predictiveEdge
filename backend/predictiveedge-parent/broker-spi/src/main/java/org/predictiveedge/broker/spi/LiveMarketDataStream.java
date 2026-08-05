package org.predictiveedge.broker.spi;

import org.predictiveedge.broker.domain.MarketDataStreamState;

/** Handle for one authenticated live market-data connection. */
public interface LiveMarketDataStream extends AutoCloseable {
    MarketDataStreamState state();
    @Override void close();
}
