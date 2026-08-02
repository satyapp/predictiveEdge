package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Stable observation family plus the exact versioned payload contract used by a revision. */
public record ObservationDescriptor(ObservationKind kind, ObservationSchemaId schemaId) {
    public ObservationDescriptor {
        Objects.requireNonNull(kind, "Observation kind is required");
        Objects.requireNonNull(schemaId, "Observation schema id is required");
    }
}
