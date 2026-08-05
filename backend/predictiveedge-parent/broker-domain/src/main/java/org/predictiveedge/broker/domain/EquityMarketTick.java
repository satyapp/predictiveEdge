package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Full-mode tick for a tradable cash-equity instrument. Volume is cumulative for the session. */
public record EquityMarketTick(
        Instrument instrument,
        String providerInstrumentId,
        BigDecimal lastPrice,
        long lastTradedQuantity,
        BigDecimal averageTradedPrice,
        long cumulativeVolume,
        long totalBuyQuantity,
        long totalSellQuantity,
        BigDecimal dayOpen,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        BigDecimal previousClose,
        Instant lastTradeTimestamp,
        Instant exchangeTimestamp,
        Instant receivedAt) implements MarketTick {

    public EquityMarketTick {
        requireIdentity(instrument, providerInstrumentId, exchangeTimestamp, receivedAt);
        requirePrice(lastPrice, "Last price"); requirePrice(averageTradedPrice, "Average traded price");
        requirePrice(dayOpen, "Day open"); requirePrice(dayHigh, "Day high");
        requirePrice(dayLow, "Day low"); requirePrice(previousClose, "Previous close");
        if (lastTradedQuantity < 0 || cumulativeVolume < 0 || totalBuyQuantity < 0 || totalSellQuantity < 0)
            throw new IllegalArgumentException("Tick quantities cannot be negative");
        if (dayHigh.compareTo(dayOpen) < 0 || dayHigh.compareTo(dayLow) < 0
                || dayLow.compareTo(dayOpen) > 0 || dayHigh.compareTo(lastPrice) < 0
                || dayLow.compareTo(lastPrice) > 0)
            throw new IllegalArgumentException("Tick day range is invalid");
        if (lastTradeTimestamp != null && lastTradeTimestamp.isAfter(exchangeTimestamp))
            throw new IllegalArgumentException("Last trade cannot follow exchange timestamp");
    }

    private static void requireIdentity(Instrument instrument, String providerId, Instant exchangeAt, Instant receivedAt) {
        Objects.requireNonNull(instrument, "Instrument is required");
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("Provider instrument id is required");
        Objects.requireNonNull(exchangeAt, "Exchange timestamp is required");
        Objects.requireNonNull(receivedAt, "Receipt timestamp is required");
        if (receivedAt.plusSeconds(5).isBefore(exchangeAt))
            throw new IllegalArgumentException("Exchange timestamp is implausibly ahead of receipt time");
    }

    private static void requirePrice(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
    }
}
