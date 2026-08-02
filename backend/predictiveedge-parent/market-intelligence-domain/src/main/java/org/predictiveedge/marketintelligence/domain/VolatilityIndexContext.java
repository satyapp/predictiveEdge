package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Point-in-time external volatility-index state; the index is sourced, not reconstructed from cash bars. */
public record VolatilityIndexContext(BigDecimal level, BigDecimal changePercent, MarketStressState state,
        Instant eventTime, Instant availableAt, String policyVersion, ContentHash inputManifestHash) {
    public VolatilityIndexContext {
        Objects.requireNonNull(level); Objects.requireNonNull(changePercent); Objects.requireNonNull(state);
        Objects.requireNonNull(eventTime); Objects.requireNonNull(availableAt); Objects.requireNonNull(inputManifestHash);
        if (level.signum() < 0 || availableAt.isBefore(eventTime)) throw new IllegalArgumentException("VIX context values are invalid");
        if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("VIX policy version is required");
        policyVersion = policyVersion.trim();
    }

    public static VolatilityIndexContext classify(BigDecimal level, BigDecimal priorClose, Instant eventTime,
            Instant availableAt, EvaluationCutoff cutoff, BigDecimal low, BigDecimal elevated, BigDecimal extreme,
            String policyVersion, ContentHash manifestHash) {
        if (eventTime.isAfter(cutoff.analysisCutoff()) || availableAt.isAfter(cutoff.knowledgeCutoff()))
            throw new IllegalArgumentException("VIX value is not causally eligible");
        if (priorClose.signum() <= 0 || low.compareTo(elevated) >= 0 || elevated.compareTo(extreme) >= 0)
            throw new IllegalArgumentException("VIX inputs or thresholds are invalid");
        var state = level.compareTo(low) < 0 ? MarketStressState.LOW
                : level.compareTo(elevated) < 0 ? MarketStressState.NORMAL
                : level.compareTo(extreme) < 0 ? MarketStressState.ELEVATED : MarketStressState.EXTREME;
        var change = level.subtract(priorClose).multiply(BigDecimal.valueOf(100))
                .divide(priorClose, FeatureCalculatorSupport.MATH_CONTEXT);
        return new VolatilityIndexContext(level, change, state, eventTime, availableAt, policyVersion, manifestHash);
    }
}
