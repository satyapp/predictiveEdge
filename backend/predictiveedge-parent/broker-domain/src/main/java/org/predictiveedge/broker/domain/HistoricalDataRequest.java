package org.predictiveedge.broker.domain;

import java.time.Instant;
import java.util.Objects;

public record HistoricalDataRequest(
        Instrument instrument,
        String providerInstrumentId,
        CandleInterval interval,
        Instant from,
        Instant to,
        boolean continuous,
        boolean includeOpenInterest) {

    public HistoricalDataRequest {
        Objects.requireNonNull(instrument, "Instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank()) {
            throw new IllegalArgumentException("Provider instrument id is required");
        }
        Objects.requireNonNull(interval, "Candle interval is required");
        Objects.requireNonNull(from, "From time is required");
        Objects.requireNonNull(to, "To time is required");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("From time must be before to time");
        }
    }
}
