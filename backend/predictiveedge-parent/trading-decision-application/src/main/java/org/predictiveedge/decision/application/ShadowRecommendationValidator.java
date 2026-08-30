package org.predictiveedge.decision.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;

/** Reject-only policy gate. It never creates or changes a recommendation. */
public final class ShadowRecommendationValidator {
    private final BigDecimal minimumDirectionalProbability;

    public ShadowRecommendationValidator(BigDecimal minimumDirectionalProbability) {
        Objects.requireNonNull(minimumDirectionalProbability, "Minimum directional probability is required");
        if (minimumDirectionalProbability.signum() < 0 || minimumDirectionalProbability.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Minimum directional probability must be between zero and one");
        }
        this.minimumDirectionalProbability = minimumDirectionalProbability;
    }

    public RecommendationValidationResult validate(
            AITradingDecisionInputBundle bundle, AIRecommendation recommendation) {
        Objects.requireNonNull(bundle, "Input bundle is required");
        Objects.requireNonNull(recommendation, "AI recommendation is required");
        List<String> reasons = new ArrayList<>();
        if (!recommendation.bundleId().equals(bundle.bundleId())) reasons.add("BUNDLE_MISMATCH");
        if (!recommendation.scope().equals(bundle.scope())) reasons.add("SCOPE_MISMATCH");
        if (recommendation.generatedAt().isBefore(bundle.assembledAt())) reasons.add("NON_CAUSAL_MODEL_TIME");
        if (!bundle.isReady()) reasons.add("INPUT_BUNDLE_NOT_READY");
        requirePass(bundle, DecisionResourceType.RISK, reasons);
        requirePass(bundle, DecisionResourceType.PORTFOLIO, reasons);
        requirePass(bundle, DecisionResourceType.VALIDATION, reasons);
        requirePass(bundle, DecisionResourceType.EXECUTION, reasons);
        if (recommendation.action().isDirectional()) {
            if (recommendation.expectedValueAfterCosts().signum() <= 0) reasons.add("NON_POSITIVE_EXPECTED_VALUE");
            if (recommendation.calibratedWinProbability().compareTo(minimumDirectionalProbability) < 0) {
                reasons.add("CONFIDENCE_BELOW_POLICY");
            }
            if (!recommendation.entryValidUntil().isBefore(bundle.executionContext().validUntil())
                    && !recommendation.entryValidUntil().equals(bundle.executionContext().validUntil())) {
                reasons.add("ENTRY_WINDOW_EXCEEDS_EXECUTION_CONTEXT");
            }
        }
        return reasons.isEmpty() ? RecommendationValidationResult.pass()
                : RecommendationValidationResult.rejected(List.copyOf(reasons));
    }

    private static void requirePass(AITradingDecisionInputBundle bundle, DecisionResourceType type, List<String> reasons) {
        if (bundle.resources().get(type).gateDisposition() != GateDisposition.PASS) {
            reasons.add(type.name() + "_GATE_NOT_PASS");
        }
    }
}
