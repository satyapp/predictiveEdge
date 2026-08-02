package org.predictiveedge.marketintelligence.domain;

import java.time.Duration;
import java.util.Objects;

/** Typed final-bar input contract for one feature definition. */
public record BarInputRequirement(
        BarTimeframe timeframe,
        int requiredBars,
        Duration maximumStaleness,
        boolean resetsAtSessionBoundary) {

    public BarInputRequirement {
        Objects.requireNonNull(timeframe, "Input timeframe is required");
        if (requiredBars < 1) {
            throw new IllegalArgumentException("At least one input bar is required");
        }
        Objects.requireNonNull(maximumStaleness, "Maximum staleness is required");
        if (maximumStaleness.isNegative() || maximumStaleness.isZero()) {
            throw new IllegalArgumentException("Maximum staleness must be positive");
        }
    }
}
