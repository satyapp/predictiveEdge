package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Exact identity of a feature definition; consumers must never resolve an implicit latest version. */
public record FeatureDefinitionRef(FeatureId featureId, String version) {
    public FeatureDefinitionRef {
        Objects.requireNonNull(featureId, "Feature id is required");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Feature definition version is required");
        }
        version = version.trim();
    }
}
