package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable semantic Market Context; persistence identity/version is added by MI-1.1 publication. */
public record MarketContextSnapshot(
        MarketContextKey key,
        EvaluationCutoff cutoff,
        Instant observedThrough,
        Instant decisionReadyAt,
        Instant expiresAt,
        MarketRegime regime,
        Map<EvidenceDimension, DimensionAssessment> dimensions,
        int confidence,
        ConfidenceBand confidenceBand,
        List<FeatureValue> features,
        List<MarketEvidence> evidence,
        QualityAssessment quality,
        String fusionPolicyVersion,
        String contextPolicyVersion,
        ContentHash inputLineageHash,
        ContentHash semanticHash) {

    public MarketContextSnapshot {
        Objects.requireNonNull(key); Objects.requireNonNull(cutoff); Objects.requireNonNull(observedThrough);
        Objects.requireNonNull(decisionReadyAt); Objects.requireNonNull(expiresAt); Objects.requireNonNull(regime);
        if (!decisionReadyAt.isBefore(expiresAt)) throw new IllegalArgumentException("Context expiry must follow readiness");
        var dimensionCopy = new EnumMap<EvidenceDimension, DimensionAssessment>(EvidenceDimension.class);
        dimensionCopy.putAll(Objects.requireNonNull(dimensions));
        for (EvidenceDimension dimension : EvidenceDimension.values()) if (!dimensionCopy.containsKey(dimension))
            throw new IllegalArgumentException("Missing context dimension " + dimension);
        dimensions = Map.copyOf(dimensionCopy);
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Context confidence is outside 0-100");
        Objects.requireNonNull(confidenceBand);
        features = List.copyOf(Objects.requireNonNull(features).stream()
                .sorted(Comparator.comparing(value -> value.definitionRef().featureId().value())).toList());
        evidence = List.copyOf(Objects.requireNonNull(evidence).stream()
                .sorted(Comparator.comparing(value -> value.contentHash().value())).toList());
        Objects.requireNonNull(quality);
        fusionPolicyVersion = required(fusionPolicyVersion); contextPolicyVersion = required(contextPolicyVersion);
        Objects.requireNonNull(inputLineageHash); Objects.requireNonNull(semanticHash);
        if (!semanticHash.equals(MarketContextHash.semanticHash(key, cutoff, observedThrough, decisionReadyAt,
                expiresAt, regime, dimensions, confidence, confidenceBand, features, evidence, quality,
                fusionPolicyVersion, contextPolicyVersion, inputLineageHash)))
            throw new IllegalArgumentException("Market Context semantic hash does not match its contents");
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Context policy version is required");
        return value.trim();
    }
}
