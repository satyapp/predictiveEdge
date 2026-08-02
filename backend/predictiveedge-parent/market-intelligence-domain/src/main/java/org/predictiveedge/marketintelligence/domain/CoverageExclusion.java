package org.predictiveedge.marketintelligence.domain;

/** Explainable removal from a coverage denominator. */
public record CoverageExclusion(String reason, int count) {
    public CoverageExclusion {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Coverage exclusion reason is required");
        }
        reason = reason.trim();
        if (count < 1) {
            throw new IllegalArgumentException("Coverage exclusion count must be positive");
        }
    }
}
