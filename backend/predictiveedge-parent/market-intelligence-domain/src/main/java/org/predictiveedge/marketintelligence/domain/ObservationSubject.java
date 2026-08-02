package org.predictiveedge.marketintelligence.domain;

import java.util.Locale;
import java.util.Objects;

/** Provider-neutral identity of the entity or aggregate described by an observation. */
public record ObservationSubject(ObservationSubjectType type, String id) {
    public ObservationSubject {
        Objects.requireNonNull(type, "Observation subject type is required");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Observation subject id is required");
        }
        id = id.trim().toUpperCase(Locale.ROOT);
    }
}
