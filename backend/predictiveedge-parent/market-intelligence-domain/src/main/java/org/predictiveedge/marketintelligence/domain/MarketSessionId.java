package org.predictiveedge.marketintelligence.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Stable identity of one venue trading session, including special sessions. */
public record MarketSessionId(String venue, LocalDate tradingDate, String sessionCode) {
    public MarketSessionId {
        venue = requiredCode(venue, "Venue");
        Objects.requireNonNull(tradingDate, "Trading date is required");
        sessionCode = requiredCode(sessionCode, "Session code");
    }

    private static String requiredCode(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
