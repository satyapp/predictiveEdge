package org.predictiveedge.chart.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Persistable Stage-2 result linked to the exact Stage-1 source fingerprint. */
public record AdvancedAnalysisResult(
        String modelId,
        Instant generatedAt,
        String inputFingerprint,
        String recommendation,
        BigDecimal confidencePercent,
        String rationale,
        List<String> enhancements,
        List<String> risks) {

    public AdvancedAnalysisResult {
        modelId = required(modelId, "Model id");
        Objects.requireNonNull(generatedAt, "Generated-at time is required");
        inputFingerprint = required(inputFingerprint, "Input fingerprint");
        recommendation = required(recommendation, "Recommendation");
        Objects.requireNonNull(confidencePercent, "Confidence is required");
        if (confidencePercent.signum() < 0 || confidencePercent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Confidence must be between 0 and 100");
        rationale = required(rationale, "Rationale");
        enhancements = copyStrings(enhancements, "Enhancements");
        risks = copyStrings(risks, "Risks");
    }

    private static List<String> copyStrings(List<String> values, String label) {
        Objects.requireNonNull(values, label + " are required");
        if (values.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException(label + " cannot contain blanks");
        return values.stream().map(String::trim).toList();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
