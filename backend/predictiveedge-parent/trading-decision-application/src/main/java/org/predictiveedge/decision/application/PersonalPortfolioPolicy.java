package org.predictiveedge.decision.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record PersonalPortfolioPolicy(
        String version, BigDecimal maximumSinglePositionWeightPercent,
        boolean allowShortPositions, Duration maximumAccountAge, Duration validity) {
    public PersonalPortfolioPolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Portfolio policy version is required");
        version = version.trim();
        Objects.requireNonNull(maximumSinglePositionWeightPercent, "Maximum position weight is required");
        if (maximumSinglePositionWeightPercent.signum() <= 0
                || maximumSinglePositionWeightPercent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Maximum position weight must be within (0,100]");
        positive(maximumAccountAge, "Maximum account age");
        positive(validity, "Portfolio validity");
    }
    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}
