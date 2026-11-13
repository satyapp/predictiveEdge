package org.predictiveedge.chart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Normalized row emitted by any supported NSE market-screen CSV adapter. */
public record MarketScreenObservation(
        MarketScreenType screenType,
        String venue,
        String symbol,
        String series,
        int sourceRank,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        BigDecimal lastPrice,
        BigDecimal percentChange,
        long volume,
        BigDecimal turnover,
        String corporateAction,
        Instant observedAt) {

    public MarketScreenObservation {
        Objects.requireNonNull(screenType, "Screen type is required");
        venue = required(venue, "Venue").toUpperCase(java.util.Locale.ROOT);
        symbol = required(symbol, "Symbol").toUpperCase(java.util.Locale.ROOT);
        series = required(series, "Series").toUpperCase(java.util.Locale.ROOT);
        if (sourceRank < 1) throw new IllegalArgumentException("Source rank must be positive");
        Objects.requireNonNull(open, "Open is required");
        Objects.requireNonNull(high, "High is required");
        Objects.requireNonNull(low, "Low is required");
        Objects.requireNonNull(previousClose, "Previous close is required");
        Objects.requireNonNull(lastPrice, "Last price is required");
        Objects.requireNonNull(percentChange, "Percent change is required");
        Objects.requireNonNull(turnover, "Turnover is required");
        Objects.requireNonNull(observedAt, "Observation timestamp is required");
        if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0
                || previousClose.signum() <= 0 || lastPrice.signum() <= 0)
            throw new IllegalArgumentException("Prices must be positive");
        if (high.compareTo(low) < 0) throw new IllegalArgumentException("High cannot be below low");
        if (lastPrice.compareTo(low) < 0 || lastPrice.compareTo(high) > 0)
            throw new IllegalArgumentException("LTP must be inside the reported high-low range");
        if (volume < 0) throw new IllegalArgumentException("Volume cannot be negative");
        if (turnover.signum() < 0) throw new IllegalArgumentException("Turnover cannot be negative");
        corporateAction = corporateAction == null ? "" : corporateAction.trim();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
