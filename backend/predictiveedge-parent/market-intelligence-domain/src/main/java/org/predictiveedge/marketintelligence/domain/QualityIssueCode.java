package org.predictiveedge.marketintelligence.domain;

/** Closed vocabulary of quality findings understood by the versioned policy. */
public enum QualityIssueCode {
    DUPLICATE_DELIVERY(QualityDimension.VALIDITY),
    OUT_OF_ORDER_INPUT(QualityDimension.TEMPORAL_INTEGRITY),
    LATE_CORRECTION(QualityDimension.TEMPORAL_INTEGRITY),
    MANDATORY_SOURCE_MISSING(QualityDimension.COMPLETENESS),
    SOURCE_CONFLICT(QualityDimension.CONSISTENCY),
    COVERAGE_LOSS(QualityDimension.COVERAGE),
    STALE_FALLBACK(QualityDimension.FRESHNESS),
    INVALID_ENTITLEMENT(QualityDimension.ENTITLEMENT),
    INVALID_LINEAGE(QualityDimension.LINEAGE),
    INVALID_SESSION(QualityDimension.SESSION),
    PROVISIONAL_INPUT(QualityDimension.FINALITY),
    FEATURE_UNAVAILABLE(QualityDimension.COMPLETENESS),
    FEATURE_WARMING_UP(QualityDimension.COMPLETENESS),
    FEATURE_STALE(QualityDimension.FRESHNESS),
    FEATURE_INVALID(QualityDimension.VALIDITY);

    private final QualityDimension dimension;

    QualityIssueCode(QualityDimension dimension) {
        this.dimension = dimension;
    }

    public QualityDimension dimension() {
        return dimension;
    }
}
