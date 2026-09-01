package org.predictiveedge.decision.application;

import java.util.Objects;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RiskSnapshot;

public record SafetyAssessmentResult(
        ExecutionEvidenceSnapshot execution, RiskSnapshot risk, PortfolioSnapshot portfolio) {
    public SafetyAssessmentResult {
        Objects.requireNonNull(execution, "Execution snapshot is required");
        Objects.requireNonNull(risk, "Risk snapshot is required");
        Objects.requireNonNull(portfolio, "Portfolio snapshot is required");
    }
}
