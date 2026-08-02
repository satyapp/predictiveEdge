package org.predictiveedge.marketintelligence.domain;

import java.util.Locale;

/** Versioned provider-neutral payload schema identity, for example {@code market.bar.v1}. */
public record ObservationSchemaId(String value) {
    private static final String FORMAT = "[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\\.v[1-9][0-9]*";

    public ObservationSchemaId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Observation schema id is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches(FORMAT)) {
            throw new IllegalArgumentException(
                    "Observation schema id must be a lowercase namespace ending in a positive major version");
        }
    }
}
