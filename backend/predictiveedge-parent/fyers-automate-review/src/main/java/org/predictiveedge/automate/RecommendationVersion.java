package org.predictiveedge.automate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One immutable, auditable version of a recommendation. */
public record RecommendationVersion(
        UUID versionId,
        RecommendationStage stage,
        RecommendationDraft draft,
        String source,
        Instant createdAt) {

    public RecommendationVersion {
        Objects.requireNonNull(versionId, "Version id is required");
        Objects.requireNonNull(stage, "Stage is required");
        Objects.requireNonNull(draft, "Draft is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Source is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
    }
}
