package org.predictiveedge.marketintelligence.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Final exchange pre-open auction facts before tenant and receipt metadata are attached. */
public record PreOpenAuctionObservation(
        String venue,
        String symbol,
        String series,
        LocalDate tradingDate,
        BigDecimal previousClose,
        BigDecimal auctionPrice,
        long auctionVolume,
        BigDecimal auctionTurnover,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        Instant observedAt) {

    public PreOpenAuctionObservation {
        venue = required(venue, "Venue").toUpperCase(Locale.ROOT);
        symbol = required(symbol, "Symbol").toUpperCase(Locale.ROOT);
        series = required(series, "Series").toUpperCase(Locale.ROOT);
        Objects.requireNonNull(tradingDate, "Trading date is required");
        positive(previousClose, "Previous close");
        positive(auctionPrice, "Auction price");
        if (auctionVolume < 0) throw new IllegalArgumentException("Auction volume cannot be negative");
        nonNegative(auctionTurnover, "Auction turnover");
        positive(bestBid, "Best bid");
        positive(bestAsk, "Best ask");
        if (bestBid.compareTo(bestAsk) > 0) throw new IllegalArgumentException("Best bid cannot exceed best ask");
        Objects.requireNonNull(observedAt, "Observation timestamp is required");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private static void positive(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static void nonNegative(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
    }
}
