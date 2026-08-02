package org.predictiveedge.marketintelligence.domain;

import java.util.List;
import java.util.Objects;

/** Provider-neutral fact presented to the quality policy for deterministic assessment. */
public record QualityFinding(
        QualityIssueCode code,
        String affectedComponent,
        String detail,
        List<String> evidenceRefs) {

    public QualityFinding {
        Objects.requireNonNull(code, "Quality issue code is required");
        affectedComponent = required(affectedComponent, "Affected component");
        detail = required(detail, "Quality finding detail");
        evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "Evidence references are required")
                .stream().map(value -> required(value, "Evidence reference")).distinct().sorted().toList());
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
