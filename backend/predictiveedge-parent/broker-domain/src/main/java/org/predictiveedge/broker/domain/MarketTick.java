package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Provider-normalized, exchange-timestamped live market observation. */
public sealed interface MarketTick permits EquityMarketTick, IndexMarketTick {
    Instrument instrument();
    String providerInstrumentId();
    BigDecimal lastPrice();
    BigDecimal dayOpen();
    BigDecimal dayHigh();
    BigDecimal dayLow();
    BigDecimal previousClose();
    Instant exchangeTimestamp();
    Instant receivedAt();
}
