package org.predictiveedge.marketintelligence.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable exhaustive policy for quality gates, penalties, unknowns and coverage. */
public final class QualityPolicy {
    private final String version;
    private final Map<QualityIssueCode, QualityRule> rules;
    private final int minimumCoverageBasisPoints;

    public QualityPolicy(
            String version,
            Map<QualityIssueCode, QualityRule> rules,
            int minimumCoverageBasisPoints) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Quality policy version is required");
        }
        this.version = version.trim();
        Objects.requireNonNull(rules, "Quality policy rules are required");
        var copy = new EnumMap<QualityIssueCode, QualityRule>(QualityIssueCode.class);
        copy.putAll(rules);
        for (QualityIssueCode code : QualityIssueCode.values()) {
            if (!copy.containsKey(code) || copy.get(code) == null) {
                throw new IllegalArgumentException("Quality policy has no rule for " + code);
            }
        }
        this.rules = Map.copyOf(copy);
        if (minimumCoverageBasisPoints < 0 || minimumCoverageBasisPoints > 10_000) {
            throw new IllegalArgumentException("Minimum coverage must be between 0 and 10000 basis points");
        }
        this.minimumCoverageBasisPoints = minimumCoverageBasisPoints;
    }

    public String version() {
        return version;
    }

    public QualityRule ruleFor(QualityIssueCode code) {
        return rules.get(Objects.requireNonNull(code, "Quality issue code is required"));
    }

    public int minimumCoverageBasisPoints() {
        return minimumCoverageBasisPoints;
    }
}
