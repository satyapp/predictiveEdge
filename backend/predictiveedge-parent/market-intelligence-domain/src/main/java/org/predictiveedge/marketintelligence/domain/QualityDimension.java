package org.predictiveedge.marketintelligence.domain;

/** Independent data-quality dimension; scores are never used to override hard gates. */
public enum QualityDimension {
    COMPLETENESS,
    FRESHNESS,
    VALIDITY,
    CONSISTENCY,
    TEMPORAL_INTEGRITY,
    LINEAGE,
    ENTITLEMENT,
    SESSION,
    COVERAGE,
    FINALITY
}
