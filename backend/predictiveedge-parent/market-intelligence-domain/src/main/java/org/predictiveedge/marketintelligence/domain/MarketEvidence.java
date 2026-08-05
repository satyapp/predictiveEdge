package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable typed claim derived from governed feature values. */
public record MarketEvidence(
        EvidenceDimension dimension,
        EvidenceState state,
        int strength,
        int uncertainty,
        String dependencyKey,
        Instant effectiveAt,
        Instant detectedAt,
        Instant expiresAt,
        List<FeatureValue> sourceFeatures,
        List<ContentHash> contextualInputHashes,
        String ruleVersion,
        ContentHash contentHash) {

    public MarketEvidence {
        Objects.requireNonNull(dimension, "Evidence dimension is required");
        Objects.requireNonNull(state, "Evidence state is required");
        validateState(dimension, state);
        if (strength < 0 || strength > 100 || uncertainty < 0 || uncertainty > 100) {
            throw new IllegalArgumentException("Evidence strength and uncertainty must be between 0 and 100");
        }
        if (dependencyKey == null || dependencyKey.isBlank()) {
            throw new IllegalArgumentException("Evidence dependency key is required");
        }
        dependencyKey = dependencyKey.trim();
        Objects.requireNonNull(effectiveAt, "Evidence effective time is required");
        Objects.requireNonNull(detectedAt, "Evidence detected time is required");
        Objects.requireNonNull(expiresAt, "Evidence expiry is required");
        if (detectedAt.isBefore(effectiveAt) || !detectedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Evidence times are inconsistent");
        }
        sourceFeatures = List.copyOf(Objects.requireNonNull(sourceFeatures, "Source features are required")
                .stream().sorted(Comparator.comparing((FeatureValue value) -> value.definitionRef().featureId().value())
                        .thenComparing(value -> value.definitionRef().version()))
                .toList());
        contextualInputHashes = List.copyOf(Objects.requireNonNull(contextualInputHashes,
                "Contextual input hashes are required").stream().distinct()
                .sorted(Comparator.comparing(ContentHash::value)).toList());
        if (sourceFeatures.isEmpty() && contextualInputHashes.isEmpty())
            throw new IllegalArgumentException("Evidence requires a feature or contextual input");
        if (ruleVersion == null || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("Evidence rule version is required");
        }
        ruleVersion = ruleVersion.trim();
        Objects.requireNonNull(contentHash, "Evidence content hash is required");
        if (!contentHash.equals(EvidenceHash.hash(dimension, state, strength, uncertainty, dependencyKey,
                effectiveAt, detectedAt, expiresAt, sourceFeatures, contextualInputHashes, ruleVersion))) {
            throw new IllegalArgumentException("Evidence hash does not match its contents");
        }
    }

    public static MarketEvidence create(EvidenceDimension dimension, EvidenceState state, int strength,
            int uncertainty, String dependencyKey, Instant effectiveAt, Instant detectedAt, Instant expiresAt,
            List<FeatureValue> sourceFeatures, String ruleVersion) {
        return new MarketEvidence(dimension, state, strength, uncertainty, dependencyKey, effectiveAt, detectedAt,
                expiresAt, sourceFeatures, List.of(), ruleVersion, EvidenceHash.hash(dimension, state, strength,
                        uncertainty, dependencyKey, effectiveAt, detectedAt, expiresAt, sourceFeatures, List.of(), ruleVersion));
    }

    public static MarketEvidence createContextual(EvidenceDimension dimension, EvidenceState state, int strength,
            int uncertainty, String dependencyKey, Instant effectiveAt, Instant detectedAt, Instant expiresAt,
            List<ContentHash> inputHashes, String ruleVersion) {
        return new MarketEvidence(dimension, state, strength, uncertainty, dependencyKey, effectiveAt, detectedAt,
                expiresAt, List.of(), inputHashes, ruleVersion, EvidenceHash.hash(dimension, state, strength,
                        uncertainty, dependencyKey, effectiveAt, detectedAt, expiresAt, List.of(), inputHashes, ruleVersion));
    }

    public int adjustedStrength() {
        return Math.max(0, strength - uncertainty);
    }

    private static void validateState(EvidenceDimension dimension, EvidenceState state) {
        boolean valid = switch (dimension) {
            case DIRECTION -> state == EvidenceState.BULLISH || state == EvidenceState.BEARISH
                    || state == EvidenceState.NEUTRAL || state == EvidenceState.UNKNOWN;
            case TREND_QUALITY -> state == EvidenceState.BULLISH || state == EvidenceState.BEARISH
                    || state == EvidenceState.WEAK || state == EvidenceState.UNKNOWN;
            case VOLATILITY -> state == EvidenceState.EXPANDING || state == EvidenceState.CONTRACTING
                    || state == EvidenceState.NORMAL || state == EvidenceState.UNKNOWN;
            case INTRADAY_LOCATION -> state == EvidenceState.ABOVE_VALUE || state == EvidenceState.BELOW_VALUE
                    || state == EvidenceState.AT_VALUE || state == EvidenceState.UNKNOWN;
            case TRIGGER -> state == EvidenceState.BREAKOUT || state == EvidenceState.BREAKDOWN
                    || state == EvidenceState.INSIDE || state == EvidenceState.UNKNOWN;
            case MOMENTUM, RELATIVE_LEADERSHIP, BREADTH -> state == EvidenceState.BULLISH
                    || state == EvidenceState.BEARISH || state == EvidenceState.NEUTRAL || state == EvidenceState.UNKNOWN;
            case PARTICIPATION -> state == EvidenceState.STRONG || state == EvidenceState.WEAK
                    || state == EvidenceState.NORMAL || state == EvidenceState.UNKNOWN;
            case RISK_DISTANCE -> state == EvidenceState.LOW || state == EvidenceState.NORMAL
                    || state == EvidenceState.HIGH || state == EvidenceState.UNKNOWN;
            case MARKET_STRESS -> state == EvidenceState.LOW || state == EvidenceState.NORMAL
                    || state == EvidenceState.ELEVATED || state == EvidenceState.EXTREME
                    || state == EvidenceState.UNKNOWN;
        };
        if (!valid) {
            throw new IllegalArgumentException("Evidence state is invalid for dimension " + dimension);
        }
    }
}
