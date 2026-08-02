package org.predictiveedge.marketintelligence.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Converts non-ready governed feature states into explicit quality findings. */
public final class FeatureQualityFindingFactory {
    private FeatureQualityFindingFactory() {
    }

    public static Optional<QualityFinding> from(
            FeatureDefinitionRef definitionRef,
            FeatureReadinessAssessment assessment) {
        Objects.requireNonNull(definitionRef, "Feature definition reference is required");
        Objects.requireNonNull(assessment, "Feature readiness assessment is required");
        if (assessment.readiness() == FeatureReadiness.READY) {
            return Optional.empty();
        }
        var code = switch (assessment.readiness()) {
            case WARMING_UP -> QualityIssueCode.FEATURE_WARMING_UP;
            case STALE -> QualityIssueCode.FEATURE_STALE;
            case UNAVAILABLE -> QualityIssueCode.FEATURE_UNAVAILABLE;
            case INVALID -> QualityIssueCode.FEATURE_INVALID;
            case READY -> throw new IllegalStateException("Ready feature has no quality finding");
        };
        String affected = definitionRef.featureId().value() + "@" + definitionRef.version();
        String detail = assessment.reason() + "; available bars " + assessment.availableBars()
                + " of " + assessment.requiredBars();
        return Optional.of(new QualityFinding(code, affected, detail, List.of()));
    }
}
