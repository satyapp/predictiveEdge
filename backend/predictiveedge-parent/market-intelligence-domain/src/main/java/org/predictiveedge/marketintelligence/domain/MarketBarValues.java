package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Canonical OHLCV values for a market bar revision. */
public record MarketBarValues(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {

    public MarketBarValues {
        requirePositive(open, "Open");
        requirePositive(high, "High");
        requirePositive(low, "Low");
        requirePositive(close, "Close");
        if (volume < 0) {
            throw new IllegalArgumentException("Volume cannot be negative");
        }
        if (high.compareTo(low) < 0
                || high.compareTo(open) < 0
                || high.compareTo(close) < 0
                || low.compareTo(open) > 0
                || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("OHLC values must be within the low-high range");
        }
    }

    private static void requirePositive(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
