package org.predictiveedge.marketintelligence.domain;

import java.util.List;
import java.util.Objects;
import java.util.Comparator;

/** Dependency-deduplicated conclusion for one market dimension. */
public record DimensionAssessment(EvidenceDimension dimension, EvidenceState state, int confidence,
                                  boolean conflict, List<ContentHash> selectedEvidence) {
    public DimensionAssessment {
        Objects.requireNonNull(dimension, "Dimension is required");
        Objects.requireNonNull(state, "Dimension state is required");
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence is outside 0-100");
        selectedEvidence = List.copyOf(Objects.requireNonNull(selectedEvidence, "Selected evidence is required")
                .stream().distinct().sorted(Comparator.comparing(ContentHash::value)).toList());
    }
}
