package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Versioned precision, rounding and comparison tolerance for deterministic calculations. */
public record NumericPolicy(
        int scale,
        RoundingMode roundingMode,
        RoundingBoundary roundingBoundary,
        BigDecimal tolerance,
        String version) {

    public NumericPolicy {
        if (scale < 0 || scale > 18) {
            throw new IllegalArgumentException("Numeric scale must be between 0 and 18");
        }
        Objects.requireNonNull(roundingMode, "Rounding mode is required");
        Objects.requireNonNull(roundingBoundary, "Rounding boundary is required");
        Objects.requireNonNull(tolerance, "Numeric tolerance is required");
        if (tolerance.signum() < 0) {
            throw new IllegalArgumentException("Numeric tolerance cannot be negative");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Numeric policy version is required");
        }
        tolerance = tolerance.stripTrailingZeros();
        version = version.trim();
    }

    public BigDecimal round(BigDecimal value) {
        Objects.requireNonNull(value, "Numeric value is required");
        return value.setScale(scale, roundingMode);
    }
}
