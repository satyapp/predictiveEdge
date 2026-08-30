package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable link for a future Trade Guardian reassessment; originals are never overwritten. */
public record RecommendationRevision(
        String revisionId,
        String originalRecommendationId,
        String previousRecommendationId,
        AIRecommendation recommendation,
        Instant recordedAt) {

    public RecommendationRevision {
        revisionId = required(revisionId, "Revision id");
        originalRecommendationId = required(originalRecommendationId, "Original recommendation id");
        previousRecommendationId = required(previousRecommendationId, "Previous recommendation id");
        Objects.requireNonNull(recommendation, "Recommendation is required");
        Objects.requireNonNull(recordedAt, "Recorded-at time is required");
        if (recommendation.recommendationId().equals(previousRecommendationId)) {
            throw new IllegalArgumentException("A revision must create a new recommendation id");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
