package org.predictiveedge.guardian.domain;

/** Minimal canonical reference until the shared instrument master is introduced. */
public record InstrumentRef(String venue, String symbol) {
    public InstrumentRef {
        venue = required(venue, "Venue").toUpperCase();
        symbol = required(symbol, "Symbol").toUpperCase();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
