package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Stable continuation position in chronological market-bar key order. */
public record MarketBarReplayCursor(
        Instant intervalStart,
        String venue,
        LocalDate tradingDate,
        String sessionCode) {
    public MarketBarReplayCursor {
        Objects.requireNonNull(intervalStart, "Cursor interval start is required");
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("Cursor venue is required");
        Objects.requireNonNull(tradingDate, "Cursor trading date is required");
        if (sessionCode == null || sessionCode.isBlank())
            throw new IllegalArgumentException("Cursor session code is required");
        venue = venue.trim().toUpperCase();
        sessionCode = sessionCode.trim().toUpperCase();
    }
}
