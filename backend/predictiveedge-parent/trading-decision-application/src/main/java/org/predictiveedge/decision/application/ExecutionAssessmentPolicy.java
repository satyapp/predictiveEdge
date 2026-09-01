package org.predictiveedge.decision.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record ExecutionAssessmentPolicy(
        String version, Duration maximumDepthAge, Duration validity,
        BigDecimal maximumSpreadBps, BigDecimal maximumSlippageBps,
        BigDecimal feesAndTaxesBps, long decisionToOrderLatencyMillis) {
    public ExecutionAssessmentPolicy {
        version = required(version, "Execution policy version");
        positive(maximumDepthAge, "Maximum depth age");
        positive(validity, "Execution validity");
        nonNegative(maximumSpreadBps, "Maximum spread bps");
        nonNegative(maximumSlippageBps, "Maximum slippage bps");
        nonNegative(feesAndTaxesBps, "Fees and taxes bps");
        if (decisionToOrderLatencyMillis < 0) throw new IllegalArgumentException("Latency cannot be negative");
    }
    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
    private static void nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " cannot be negative");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
