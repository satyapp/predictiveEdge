package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        List<MarketDepthLevel> buyDepth,
        List<MarketDepthLevel> sellDepth,
        Instant lastTradeTimestamp,
        Instant exchangeTimestamp,
        Instant receivedAt) implements MarketTick {

    public EquityMarketTick {
        requireIdentity(instrument, providerInstrumentId, exchangeTimestamp, receivedAt);
        requirePrice(lastPrice, "Last price"); requirePrice(averageTradedPrice, "Average traded price");
        requirePrice(dayOpen, "Day open"); requirePrice(dayHigh, "Day high");
        requirePrice(dayLow, "Day low"); requirePrice(previousClose, "Previous close");
        buyDepth = requireDepth(buyDepth, "Buy depth", true);
        sellDepth = requireDepth(sellDepth, "Sell depth", false);
        if (buyDepth.getFirst().price().signum() > 0 && sellDepth.getFirst().price().signum() > 0
                && sellDepth.getFirst().price().compareTo(buyDepth.getFirst().price()) < 0) {
            throw new IllegalArgumentException("Best ask cannot be below best bid");
        }
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

    private static List<MarketDepthLevel> requireDepth(
            List<MarketDepthLevel> levels, String name, boolean descending) {
        levels = List.copyOf(Objects.requireNonNull(levels, name + " is required"));
        if (levels.size() != 5) throw new IllegalArgumentException(name + " must contain exactly five levels");
        BigDecimal previous = null;
        boolean emptySeen = false;
        for (int index = 0; index < levels.size(); index++) {
            MarketDepthLevel level = Objects.requireNonNull(levels.get(index), name + " cannot contain nulls");
            if (level.position() != index + 1) throw new IllegalArgumentException(name + " positions must be ordered");
            if (level.price().signum() == 0) {
                emptySeen = true;
            } else {
                if (emptySeen) throw new IllegalArgumentException(name + " empty levels must trail populated levels");
                if (previous != null && (descending
                        ? level.price().compareTo(previous) > 0
                        : level.price().compareTo(previous) < 0)) {
                    throw new IllegalArgumentException(name + " prices are not ordered");
                }
                previous = level.price();
            }
        }
        return levels;
    }
}
