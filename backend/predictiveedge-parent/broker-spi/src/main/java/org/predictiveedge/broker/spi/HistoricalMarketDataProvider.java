package org.predictiveedge.broker.spi;

import java.util.List;

import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.HistoricalDataRequest;

public interface HistoricalMarketDataProvider {
    List<Candle> historicalCandles(BrokerContext context, HistoricalDataRequest request);
}
