package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Finding after deterministic application of the active quality policy. */
public record QualityIssue(QualityFinding finding, QualitySeverity severity, QualityAction action,
                           int dimensionPenalty, int confidenceCap, boolean producesUnknown) {
    public QualityIssue {
        Objects.requireNonNull(finding, "Quality finding is required");
        Objects.requireNonNull(severity, "Quality severity is required");
        Objects.requireNonNull(action, "Quality action is required");
        if (dimensionPenalty < 0 || dimensionPenalty > 100 || confidenceCap < 0 || confidenceCap > 100) {
            throw new IllegalArgumentException("Assessed quality values are outside their valid range");
        }
        if (action == QualityAction.BLOCK && confidenceCap != 0) {
            throw new IllegalArgumentException("A blocking quality issue must set confidence cap to zero");
        }
    }

    static QualityIssue assess(QualityFinding finding, QualityRule rule) {
        return new QualityIssue(finding, rule.severity(), rule.action(), rule.dimensionPenalty(),
                rule.confidenceCap(), rule.producesUnknown());
    }

    public QualityDimension dimension() {
        return finding.code().dimension();
    }
}
