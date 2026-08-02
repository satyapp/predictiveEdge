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
                .stream().sorted(Comparator.comparing(value -> value.definitionRef().featureId().value()))
                .toList());
        if (sourceFeatures.isEmpty()) {
            throw new IllegalArgumentException("Evidence requires at least one source feature");
        }
        if (ruleVersion == null || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("Evidence rule version is required");
        }
        ruleVersion = ruleVersion.trim();
        Objects.requireNonNull(contentHash, "Evidence content hash is required");
        if (!contentHash.equals(EvidenceHash.hash(dimension, state, strength, uncertainty, dependencyKey,
                effectiveAt, detectedAt, expiresAt, sourceFeatures, ruleVersion))) {
            throw new IllegalArgumentException("Evidence hash does not match its contents");
        }
    }

    public static MarketEvidence create(EvidenceDimension dimension, EvidenceState state, int strength,
            int uncertainty, String dependencyKey, Instant effectiveAt, Instant detectedAt, Instant expiresAt,
            List<FeatureValue> sourceFeatures, String ruleVersion) {
        return new MarketEvidence(dimension, state, strength, uncertainty, dependencyKey, effectiveAt, detectedAt,
                expiresAt, sourceFeatures, ruleVersion, EvidenceHash.hash(dimension, state, strength, uncertainty,
                        dependencyKey, effectiveAt, detectedAt, expiresAt, sourceFeatures, ruleVersion));
    }

    public int adjustedStrength() {
        return Math.max(0, strength - uncertainty);
    }

    private static void validateState(EvidenceDimension dimension, EvidenceState state) {
        boolean valid = switch (dimension) {
            case DIRECTION -> state == EvidenceState.BULLISH || state == EvidenceState.BEARISH
                    || state == EvidenceState.NEUTRAL || state == EvidenceState.UNKNOWN;
            case VOLATILITY -> state == EvidenceState.EXPANDING || state == EvidenceState.CONTRACTING
                    || state == EvidenceState.NORMAL || state == EvidenceState.UNKNOWN;
            case PARTICIPATION -> state == EvidenceState.STRONG || state == EvidenceState.WEAK
                    || state == EvidenceState.NORMAL || state == EvidenceState.UNKNOWN;
        };
        if (!valid) {
            throw new IllegalArgumentException("Evidence state is invalid for dimension " + dimension);
        }
    }
}
