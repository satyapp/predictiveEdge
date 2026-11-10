package org.predictiveedge.chart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Final exchange pre-open auction facts used to screen an intraday setup. */
public record PreOpenSnapshot(
        String venue,
        String symbol,
        String series,
        BigDecimal previousClose,
        BigDecimal auctionPrice,
        long auctionVolume,
        BigDecimal auctionTurnover,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        Instant observedAt) {

    public PreOpenSnapshot {
        venue = required(venue, "Venue").toUpperCase();
        symbol = required(symbol, "Symbol").toUpperCase();
        series = required(series, "Series").toUpperCase();
        positive(previousClose, "Previous close");
        positive(auctionPrice, "Auction price");
        if (auctionVolume < 0) throw new IllegalArgumentException("Auction volume cannot be negative");
        Objects.requireNonNull(auctionTurnover, "Auction turnover is required");
        if (auctionTurnover.signum() < 0) throw new IllegalArgumentException("Auction turnover cannot be negative");
        positive(bestBid, "Best bid");
        positive(bestAsk, "Best ask");
        if (bestAsk.compareTo(bestBid) < 0) throw new IllegalArgumentException("Best ask cannot be below best bid");
        Objects.requireNonNull(observedAt, "Observed-at time is required");
    }

    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
