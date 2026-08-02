package org.predictiveedge.marketintelligence.domain;

import java.util.List;
import java.util.Objects;

/** Deterministically evaluates warm-up and staleness from a causal input manifest. */
public final class FeatureReadinessEvaluator {
    private FeatureReadinessEvaluator() {
    }

    public static FeatureReadinessAssessment assess(
            FeatureDefinition definition,
            FeatureInputManifest manifest) {
        Objects.requireNonNull(definition, "Feature definition is required");
        Objects.requireNonNull(manifest, "Feature input manifest is required");
        var requirement = definition.inputRequirement();
        if (manifest.timeframe() != requirement.timeframe()) {
            return assessment(FeatureReadiness.INVALID, 0, requirement,
                    "Input timeframe does not match the definition");
        }
        if (manifest.bars().isEmpty()) {
            return assessment(FeatureReadiness.UNAVAILABLE, 0, requirement, "No eligible final bars");
        }

        List<MarketBarRevision> applicable = manifest.bars();
        if (requirement.resetsAtSessionBoundary()) {
            var currentSession = applicable.getLast().key().sessionId();
            applicable = applicable.stream().filter(bar -> bar.key().sessionId().equals(currentSession)).toList();
        }
        var latestEnd = applicable.getLast().key().interval().endsAt();
        if (latestEnd.plus(requirement.maximumStaleness()).isBefore(manifest.cutoff().analysisCutoff())) {
            return assessment(FeatureReadiness.STALE, applicable.size(), requirement,
                    "Latest eligible bar exceeds maximum staleness");
        }
        if (applicable.size() < requirement.requiredBars()) {
            return assessment(FeatureReadiness.WARMING_UP, applicable.size(), requirement,
                    "More final bars are required for initialization");
        }
        return assessment(FeatureReadiness.READY, applicable.size(), requirement,
                "Input contract is satisfied");
    }

    private static FeatureReadinessAssessment assessment(
            FeatureReadiness readiness,
            int available,
            BarInputRequirement requirement,
            String reason) {
        return new FeatureReadinessAssessment(readiness, available, requirement.requiredBars(), reason);
    }
}
