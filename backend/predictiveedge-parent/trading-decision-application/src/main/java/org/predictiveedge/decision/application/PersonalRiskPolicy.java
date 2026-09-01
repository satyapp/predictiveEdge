package org.predictiveedge.decision.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record PersonalRiskPolicy(
        String version, BigDecimal maximumRiskPerTrade, BigDecimal dailyLossLimit,
        BigDecimal maximumOpenRisk, BigDecimal maximumPositionValue,
        Duration maximumAccountAge, Duration validity) {
    public PersonalRiskPolicy {
        version = required(version);
        positive(maximumRiskPerTrade, "Maximum risk per trade");
        positive(dailyLossLimit, "Daily loss limit");
        positive(maximumOpenRisk, "Maximum open risk");
        positive(maximumPositionValue, "Maximum position value");
        positive(maximumAccountAge, "Maximum account age");
        positive(validity, "Risk validity");
    }
    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Risk policy version is required");
        return value.trim();
    }
}
