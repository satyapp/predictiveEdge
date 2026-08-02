package org.predictiveedge.marketintelligence.domain;

import java.util.List;
import java.util.Objects;

/** Explainable score for one dimension; it does not participate in hard-gate precedence. */
public record QualityDimensionScore(QualityDimension dimension, int score, List<QualityIssueCode> issueCodes) {
    public QualityDimensionScore {
        Objects.requireNonNull(dimension, "Quality dimension is required");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Quality dimension score must be between 0 and 100");
        }
        issueCodes = List.copyOf(Objects.requireNonNull(issueCodes, "Quality issue codes are required")
                .stream().distinct().sorted().toList());
    }
}
