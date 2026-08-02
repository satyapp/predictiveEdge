package org.predictiveedge.marketintelligence.domain;

/** Explicit feature availability; missing or immature data is never represented as zero. */
public enum FeatureReadiness {
    READY,
    WARMING_UP,
    STALE,
    UNAVAILABLE,
    INVALID
}
