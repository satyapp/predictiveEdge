package org.predictiveedge.chart.domain;

import java.util.Objects;

public record ChartAssessment(ChartAction action, int confidence, ChartAssessmentReason reason) {
    public ChartAssessment {
        Objects.requireNonNull(action, "Chart action is required");
        Objects.requireNonNull(reason, "Chart assessment reason is required");
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence must be 0-100");
        if (action == ChartAction.WAIT && confidence != 0) {
            throw new IllegalArgumentException("WAIT must have zero confidence");
        }
    }
}
