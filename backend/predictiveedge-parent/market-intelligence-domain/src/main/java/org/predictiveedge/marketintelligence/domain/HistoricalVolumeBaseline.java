package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Objects;

/** Knowledge-dated median volume for one same-session bar slot. */
public record HistoricalVolumeBaseline(String venue, BarTimeframe timeframe, int sessionSlot,
        long medianVolume, int sampleSessions, Instant availableAt, String version) {
    public HistoricalVolumeBaseline {
        if (venue == null || venue.isBlank() || version == null || version.isBlank())
            throw new IllegalArgumentException("Volume baseline venue and version are required");
        venue = venue.trim().toUpperCase(java.util.Locale.ROOT); version = version.trim();
        Objects.requireNonNull(timeframe); Objects.requireNonNull(availableAt);
        if (sessionSlot < 0 || medianVolume <= 0 || sampleSessions < 1)
            throw new IllegalArgumentException("Historical volume baseline values are invalid");
    }
}
