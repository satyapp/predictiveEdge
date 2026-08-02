package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Composes the first deterministic context snapshot from governed features, evidence and quality. */
public final class MarketContextComposer {
    private MarketContextComposer() {
    }

    public static MarketContextSnapshot compose(MarketContextKey key, EvaluationCutoff cutoff,
            Instant decisionReadyAt, Instant expiresAt, List<FeatureValue> features,
            List<MarketEvidence> evidence, QualityAssessment quality, FusionPolicy fusionPolicy,
            String contextPolicyVersion) {
        Objects.requireNonNull(key); Objects.requireNonNull(cutoff); Objects.requireNonNull(decisionReadyAt);
        Objects.requireNonNull(expiresAt); Objects.requireNonNull(features); Objects.requireNonNull(evidence);
        Objects.requireNonNull(quality); Objects.requireNonNull(fusionPolicy);
        if (contextPolicyVersion == null || contextPolicyVersion.isBlank())
            throw new IllegalArgumentException("Context policy version is required");
        if (features.isEmpty()) throw new IllegalArgumentException("Market Context requires governed features");
        if (!quality.cutoff().equals(cutoff)) throw new IllegalArgumentException("Quality cutoff does not match context cutoff");
        Instant earliestReady = cutoff.knowledgeCutoff();
        for (FeatureValue feature : features) if (feature.availableAt().isAfter(earliestReady)) earliestReady = feature.availableAt();
        for (MarketEvidence item : evidence) if (item.detectedAt().isAfter(earliestReady)) earliestReady = item.detectedAt();
        if (decisionReadyAt.isBefore(earliestReady)) throw new IllegalArgumentException("Context readiness precedes causal inputs");
        var dimensions = EvidenceFusion.fuse(evidence, quality, fusionPolicy);
        var regime = regime(dimensions.get(EvidenceDimension.DIRECTION), quality);
        int confidence = regime == MarketRegime.SUSPENDED ? 0
                : Math.min(quality.confidenceCap(), dimensions.values().stream()
                        .mapToInt(DimensionAssessment::confidence).sum() / dimensions.size());
        var band = confidence < 40 ? ConfidenceBand.LOW : confidence < 70 ? ConfidenceBand.MEDIUM : ConfidenceBand.HIGH;
        Instant observedThrough = features.stream().map(FeatureValue::observedThrough).max(Instant::compareTo).orElseThrow();
        var lineageHash = MarketContextHash.lineageHash(features);
        var semanticHash = MarketContextHash.semanticHash(key, cutoff, observedThrough, decisionReadyAt, expiresAt,
                regime, dimensions, confidence, band, features, evidence, quality, fusionPolicy.version(),
                contextPolicyVersion.trim(), lineageHash);
        return new MarketContextSnapshot(key, cutoff, observedThrough, decisionReadyAt, expiresAt, regime,
                dimensions, confidence, band, features, evidence, quality, fusionPolicy.version(),
                contextPolicyVersion, lineageHash, semanticHash);
    }

    private static MarketRegime regime(DimensionAssessment direction, QualityAssessment quality) {
        if (quality.disposition() == QualityDisposition.BLOCKED) return MarketRegime.SUSPENDED;
        return switch (direction.state()) {
            case BULLISH -> MarketRegime.BULLISH_TREND;
            case BEARISH -> MarketRegime.BEARISH_TREND;
            case NEUTRAL -> MarketRegime.RANGE;
            case MIXED -> MarketRegime.MIXED;
            default -> MarketRegime.INSUFFICIENT_EVIDENCE;
        };
    }
}
