package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Identity shared by every revision of one canonical market bar. */
public record MarketBarKey(
        ObservationSubject subject,
        MarketSessionId sessionId,
        BarTimeframe timeframe,
        BarInterval interval) {

    public MarketBarKey {
        Objects.requireNonNull(subject, "Bar subject is required");
        Objects.requireNonNull(sessionId, "Bar session id is required");
        Objects.requireNonNull(timeframe, "Bar timeframe is required");
        Objects.requireNonNull(interval, "Bar interval is required");
        if (subject.type() != ObservationSubjectType.INSTRUMENT
                && subject.type() != ObservationSubjectType.INDEX) {
            throw new IllegalArgumentException("A market bar subject must be an instrument or index");
        }
    }
}
