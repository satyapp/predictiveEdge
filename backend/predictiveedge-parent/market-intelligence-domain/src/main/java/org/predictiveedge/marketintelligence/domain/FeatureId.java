package org.predictiveedge.marketintelligence.domain;

import java.util.Locale;

/** Stable, provider-neutral identity of a governed analytical feature. */
public record FeatureId(String value) {
    private static final String PATTERN = "[A-Z][A-Z0-9_]{1,63}";

    public FeatureId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Feature id is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches(PATTERN)) {
            throw new IllegalArgumentException("Feature id must use uppercase letters, digits and underscores");
        }
    }
}
