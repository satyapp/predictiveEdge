package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Full-mode tick for an index such as NIFTY 50 or India VIX. */
public record IndexMarketTick(
        Instrument instrument,
        String providerInstrumentId,
        BigDecimal lastPrice,
        BigDecimal dayOpen,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        BigDecimal previousClose,
        BigDecimal changePercent,
        Instant exchangeTimestamp,
        Instant receivedAt) implements MarketTick {

    public IndexMarketTick {
        Objects.requireNonNull(instrument, "Instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank())
            throw new IllegalArgumentException("Provider instrument id is required");
        for (BigDecimal price : new BigDecimal[] {lastPrice, dayOpen, dayHigh, dayLow, previousClose}) {
            Objects.requireNonNull(price, "Index prices are required");
            if (price.signum() < 0) throw new IllegalArgumentException("Index prices cannot be negative");
        }
        Objects.requireNonNull(changePercent, "Index change is required");
        Objects.requireNonNull(exchangeTimestamp, "Exchange timestamp is required");
        Objects.requireNonNull(receivedAt, "Receipt timestamp is required");
        if (receivedAt.plusSeconds(5).isBefore(exchangeTimestamp))
            throw new IllegalArgumentException("Exchange timestamp is implausibly ahead of receipt time");
        if (dayHigh.compareTo(dayOpen) < 0 || dayHigh.compareTo(dayLow) < 0
                || dayLow.compareTo(dayOpen) > 0 || dayHigh.compareTo(lastPrice) < 0
                || dayLow.compareTo(lastPrice) > 0)
            throw new IllegalArgumentException("Index day range is invalid");
    }
}
