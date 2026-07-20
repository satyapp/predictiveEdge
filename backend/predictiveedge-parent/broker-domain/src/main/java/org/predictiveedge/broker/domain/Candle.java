package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Candle(
        Instrument instrument,
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        Long openInterest) {

    public Candle {
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(timestamp, "Timestamp is required");
        Objects.requireNonNull(open, "Open is required");
        Objects.requireNonNull(high, "High is required");
        Objects.requireNonNull(low, "Low is required");
        Objects.requireNonNull(close, "Close is required");
        if (open.signum() < 0 || high.signum() < 0 || low.signum() < 0 || close.signum() < 0) {
            throw new IllegalArgumentException("Candle prices cannot be negative");
        }
        if (volume < 0 || (openInterest != null && openInterest < 0)) {
            throw new IllegalArgumentException("Candle volume and open interest cannot be negative");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("Candle high/low range is invalid");
        }
    }
}
