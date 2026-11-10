package org.predictiveedge.chart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Causally available finalized OHLCV bar supplied by Market Intelligence. */
public record IntradayHistoricalBar(
        LocalDate tradingDate,
        Instant startsAt,
        Instant endsAt,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        Instant availableAt,
        String evidenceReference) {

    public IntradayHistoricalBar {
        Objects.requireNonNull(tradingDate, "Trading date is required");
        Objects.requireNonNull(startsAt, "Bar start is required");
        Objects.requireNonNull(endsAt, "Bar end is required");
        if (!startsAt.isBefore(endsAt)) throw new IllegalArgumentException("Bar start must precede bar end");
        positive(open, "Open"); positive(high, "High"); positive(low, "Low"); positive(close, "Close");
        if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0)
            throw new IllegalArgumentException("OHLC values must fall inside the low-high range");
        if (volume < 0) throw new IllegalArgumentException("Volume cannot be negative");
        Objects.requireNonNull(availableAt, "Availability time is required");
        if (availableAt.isBefore(endsAt)) throw new IllegalArgumentException("A bar cannot be available before it ends");
        if (evidenceReference == null || evidenceReference.isBlank())
            throw new IllegalArgumentException("Evidence reference is required");
        evidenceReference = evidenceReference.trim();
    }

    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
