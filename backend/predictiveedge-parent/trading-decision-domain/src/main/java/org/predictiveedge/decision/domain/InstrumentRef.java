package org.predictiveedge.decision.domain;

/** Provider-neutral instrument reference until the shared instrument master is introduced. */
public record InstrumentRef(String venue, String instrumentId) {
    public InstrumentRef {
        venue = required(venue, "Venue").toUpperCase();
        instrumentId = required(instrumentId, "Instrument id").toUpperCase();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
