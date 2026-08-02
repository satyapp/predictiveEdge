package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Produces the three typed claims in the initial deterministic context slice. */
public final class InitialEvidenceFactory {
    private InitialEvidenceFactory() {
    }

    public static MarketEvidence direction(FeatureValue fastEma, FeatureValue slowEma, Instant expiresAt,
            InitialEvidencePolicy policy) {
        compatible(fastEma, slowEma); Objects.requireNonNull(policy);
        int comparison = fastEma.value().compareTo(slowEma.value());
        EvidenceState state = comparison > 0 ? EvidenceState.BULLISH
                : comparison < 0 ? EvidenceState.BEARISH : EvidenceState.NEUTRAL;
        BigDecimal gapBasisPoints = fastEma.value().subtract(slowEma.value()).abs()
                .multiply(BigDecimal.valueOf(10_000), FeatureCalculatorSupport.MATH_CONTEXT)
                .divide(slowEma.value().abs(), FeatureCalculatorSupport.MATH_CONTEXT);
        int strength = comparison == 0 ? 50 : Math.min(100, Math.max(1, gapBasisPoints.intValue()));
        return evidence(EvidenceDimension.DIRECTION, state, strength, "DIRECTION:EMA", expiresAt,
                List.of(fastEma, slowEma), policy);
    }

    public static MarketEvidence volatility(FeatureValue atr, BigDecimal lastClose, Instant expiresAt,
            InitialEvidencePolicy policy) {
        Objects.requireNonNull(atr); Objects.requireNonNull(lastClose); Objects.requireNonNull(policy);
        if (lastClose.signum() <= 0) throw new IllegalArgumentException("Last close must be positive");
        BigDecimal atrPercent = atr.value().multiply(BigDecimal.valueOf(100), FeatureCalculatorSupport.MATH_CONTEXT)
                .divide(lastClose, FeatureCalculatorSupport.MATH_CONTEXT);
        EvidenceState state = atrPercent.compareTo(policy.lowAtrPercent()) < 0 ? EvidenceState.CONTRACTING
                : atrPercent.compareTo(policy.highAtrPercent()) > 0 ? EvidenceState.EXPANDING : EvidenceState.NORMAL;
        return evidence(EvidenceDimension.VOLATILITY, state, 70, "VOLATILITY:ATR", expiresAt,
                List.of(atr), policy);
    }

    public static MarketEvidence participation(FeatureValue relativeVolume, Instant expiresAt,
            InitialEvidencePolicy policy) {
        Objects.requireNonNull(relativeVolume); Objects.requireNonNull(policy);
        EvidenceState state = relativeVolume.value().compareTo(policy.weakRelativeVolume()) < 0 ? EvidenceState.WEAK
                : relativeVolume.value().compareTo(policy.strongRelativeVolume()) > 0
                        ? EvidenceState.STRONG : EvidenceState.NORMAL;
        return evidence(EvidenceDimension.PARTICIPATION, state, 70, "PARTICIPATION:VOLUME", expiresAt,
                List.of(relativeVolume), policy);
    }

    private static MarketEvidence evidence(EvidenceDimension dimension, EvidenceState state, int strength,
            String dependency, Instant expiresAt, List<FeatureValue> features, InitialEvidencePolicy policy) {
        Instant effectiveAt = features.stream().map(FeatureValue::valueTime).max(Instant::compareTo).orElseThrow();
        Instant detectedAt = features.stream().map(FeatureValue::availableAt).max(Instant::compareTo).orElseThrow();
        return MarketEvidence.create(dimension, state, strength, policy.uncertainty(), dependency,
                effectiveAt, detectedAt, expiresAt, features, policy.version());
    }

    private static void compatible(FeatureValue left, FeatureValue right) {
        if (!left.subject().equals(right.subject()) || left.timeframe() != right.timeframe()
                || !left.valueTime().equals(right.valueTime()))
            throw new IllegalArgumentException("Compared feature values are not synchronized");
    }
}
