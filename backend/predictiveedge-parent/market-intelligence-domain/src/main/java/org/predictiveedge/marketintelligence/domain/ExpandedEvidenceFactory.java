package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Maps the complete initial equity feature profile to independent, dependency-labelled claims. */
public final class ExpandedEvidenceFactory {
    private ExpandedEvidenceFactory() { }

    public static MarketEvidence trendQuality(FeatureValue plusDi, FeatureValue minusDi, FeatureValue adx,
            Instant expiresAt, ExpandedEvidencePolicy policy) {
        EvidenceState state = adx.value().compareTo(policy.strongAdx()) < 0 ? EvidenceState.WEAK
                : plusDi.value().compareTo(minusDi.value()) > 0 ? EvidenceState.BULLISH
                : plusDi.value().compareTo(minusDi.value()) < 0 ? EvidenceState.BEARISH : EvidenceState.WEAK;
        return feature(EvidenceDimension.TREND_QUALITY, state, 80, "TREND_QUALITY:DMI_ADX", expiresAt,
                List.of(plusDi, minusDi, adx), policy);
    }

    public static MarketEvidence volatility(FeatureValue width, Instant expiresAt, ExpandedEvidencePolicy policy) {
        EvidenceState state = width.value().compareTo(policy.lowBandWidth()) < 0 ? EvidenceState.CONTRACTING
                : width.value().compareTo(policy.highBandWidth()) > 0 ? EvidenceState.EXPANDING : EvidenceState.NORMAL;
        return feature(EvidenceDimension.VOLATILITY, state, 70, "VOLATILITY:BOLLINGER_WIDTH", expiresAt,
                List.of(width), policy);
    }

    public static MarketEvidence location(FeatureValue vwap, BigDecimal close, Instant expiresAt,
            ExpandedEvidencePolicy policy) {
        if (close == null || close.signum() <= 0 || vwap.value().signum() <= 0)
            throw new IllegalArgumentException("VWAP and close must be positive");
        BigDecimal deviation = close.subtract(vwap.value()).multiply(BigDecimal.valueOf(100))
                .divide(vwap.value(), FeatureCalculatorSupport.MATH_CONTEXT);
        EvidenceState state = deviation.abs().compareTo(policy.valueTolerancePercent()) <= 0 ? EvidenceState.AT_VALUE
                : deviation.signum() > 0 ? EvidenceState.ABOVE_VALUE : EvidenceState.BELOW_VALUE;
        return feature(EvidenceDimension.INTRADAY_LOCATION, state, 70, "LOCATION:SESSION_VWAP", expiresAt,
                List.of(vwap), policy);
    }

    public static MarketEvidence trigger(FeatureValue upper, FeatureValue lower, BigDecimal signalClose,
            Instant expiresAt, ExpandedEvidencePolicy policy) {
        requireSynchronized(List.of(upper, lower));
        if (signalClose == null || signalClose.signum() <= 0 || upper.value().compareTo(lower.value()) < 0)
            throw new IllegalArgumentException("Donchian inputs are invalid");
        EvidenceState state = signalClose.compareTo(upper.value()) > 0 ? EvidenceState.BREAKOUT
                : signalClose.compareTo(lower.value()) < 0 ? EvidenceState.BREAKDOWN : EvidenceState.INSIDE;
        return feature(EvidenceDimension.TRIGGER, state, state == EvidenceState.INSIDE ? 50 : 80,
                "TRIGGER:DONCHIAN", expiresAt, List.of(upper, lower), policy);
    }

    public static MarketEvidence momentum(FeatureValue rsi, Instant expiresAt, ExpandedEvidencePolicy policy) {
        EvidenceState state = rsi.value().compareTo(policy.bullishRsi()) > 0 ? EvidenceState.BULLISH
                : rsi.value().compareTo(policy.bearishRsi()) < 0 ? EvidenceState.BEARISH : EvidenceState.NEUTRAL;
        return feature(EvidenceDimension.MOMENTUM, state, 70, "MOMENTUM:RSI", expiresAt, List.of(rsi), policy);
    }

    public static MarketEvidence riskDistance(FeatureValue atr, BigDecimal close, Instant expiresAt,
            ExpandedEvidencePolicy policy) {
        if (close == null || close.signum() <= 0 || atr.value().signum() < 0)
            throw new IllegalArgumentException("ATR and close are invalid");
        BigDecimal percent = atr.value().multiply(BigDecimal.valueOf(100))
                .divide(close, FeatureCalculatorSupport.MATH_CONTEXT);
        EvidenceState state = percent.compareTo(policy.lowAtrPercent()) < 0 ? EvidenceState.LOW
                : percent.compareTo(policy.highAtrPercent()) > 0 ? EvidenceState.HIGH : EvidenceState.NORMAL;
        return feature(EvidenceDimension.RISK_DISTANCE, state, 70, "RISK_DISTANCE:ATR", expiresAt,
                List.of(atr), policy);
    }

    public static MarketEvidence relativeLeadership(FeatureValue leadership, Instant expiresAt,
            ExpandedEvidencePolicy policy) {
        EvidenceState state = leadership.value().abs().compareTo(policy.leadershipTolerancePercent()) <= 0
                ? EvidenceState.NEUTRAL : leadership.value().signum() > 0 ? EvidenceState.BULLISH : EvidenceState.BEARISH;
        return feature(EvidenceDimension.RELATIVE_LEADERSHIP, state, 70, "LEADERSHIP:RATIO", expiresAt,
                List.of(leadership), policy);
    }

    public static MarketEvidence breadth(AdvanceDeclineSnapshot breadth, Instant expiresAt,
            ExpandedEvidencePolicy policy) {
        EvidenceState state = breadth.netBreadthPercent().compareTo(policy.bullishBreadthPercent()) > 0
                ? EvidenceState.BULLISH
                : breadth.netBreadthPercent().compareTo(policy.bearishBreadthPercent()) < 0
                        ? EvidenceState.BEARISH : EvidenceState.NEUTRAL;
        return MarketEvidence.createContextual(EvidenceDimension.BREADTH, state, 70, policy.uncertainty(),
                "BREADTH:ADVANCE_DECLINE", breadth.cutoff().analysisCutoff(), breadth.cutoff().knowledgeCutoff(),
                expiresAt, List.of(breadth.inputManifestHash()), policy.version());
    }

    public static MarketEvidence marketStress(VolatilityIndexContext vix, Instant expiresAt,
            ExpandedEvidencePolicy policy) {
        EvidenceState state = switch (vix.state()) {
            case LOW -> EvidenceState.LOW; case NORMAL -> EvidenceState.NORMAL;
            case ELEVATED -> EvidenceState.ELEVATED; case EXTREME -> EvidenceState.EXTREME;
        };
        return MarketEvidence.createContextual(EvidenceDimension.MARKET_STRESS, state, 80, policy.uncertainty(),
                "MARKET_STRESS:INDIA_VIX", vix.eventTime(), vix.availableAt(), expiresAt,
                List.of(vix.inputManifestHash()), policy.version());
    }

    private static MarketEvidence feature(EvidenceDimension dimension, EvidenceState state, int strength,
            String dependency, Instant expiresAt, List<FeatureValue> features, ExpandedEvidencePolicy policy) {
        Objects.requireNonNull(policy);
        requireSynchronized(features);
        Instant effective = features.stream().map(FeatureValue::valueTime).max(Instant::compareTo).orElseThrow();
        Instant detected = features.stream().map(FeatureValue::availableAt).max(Instant::compareTo).orElseThrow();
        return MarketEvidence.create(dimension, state, strength, policy.uncertainty(), dependency,
                effective, detected, expiresAt, features, policy.version());
    }

    private static void requireSynchronized(List<FeatureValue> features) {
        if (features == null || features.isEmpty() || features.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Evidence features are required");
        var first = features.getFirst();
        if (features.stream().anyMatch(value -> !value.subject().equals(first.subject())
                || value.timeframe() != first.timeframe() || !value.valueTime().equals(first.valueTime())))
            throw new IllegalArgumentException("Evidence features must share subject, timeframe and value time");
    }
}
