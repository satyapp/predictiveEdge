package org.predictiveedge.marketintelligence.domain;

import java.util.Objects;

/** Versioned policy response to one quality issue code. */
public record QualityRule(
        QualitySeverity severity,
        QualityAction action,
        int dimensionPenalty,
        int confidenceCap,
        boolean producesUnknown) {

    public QualityRule {
        Objects.requireNonNull(severity, "Quality severity is required");
        Objects.requireNonNull(action, "Quality action is required");
        if (dimensionPenalty < 0 || dimensionPenalty > 100) {
            throw new IllegalArgumentException("Dimension penalty must be between 0 and 100");
        }
        if (confidenceCap < 0 || confidenceCap > 100) {
            throw new IllegalArgumentException("Confidence cap must be between 0 and 100");
        }
        if (action == QualityAction.BLOCK && confidenceCap != 0) {
            throw new IllegalArgumentException("A blocking quality rule must set confidence cap to zero");
        }
    }
}
