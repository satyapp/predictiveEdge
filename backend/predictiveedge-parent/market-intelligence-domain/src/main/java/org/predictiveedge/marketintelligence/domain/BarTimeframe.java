package org.predictiveedge.marketintelligence.domain;

import java.time.Duration;

/** Supported fixed-duration intraday bar resolutions. */
public enum BarTimeframe {
    ONE_MINUTE(Duration.ofMinutes(1)),
    FIVE_MINUTES(Duration.ofMinutes(5)),
    FIFTEEN_MINUTES(Duration.ofMinutes(15)),
    ONE_HOUR(Duration.ofHours(1));

    private final Duration duration;

    BarTimeframe(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
