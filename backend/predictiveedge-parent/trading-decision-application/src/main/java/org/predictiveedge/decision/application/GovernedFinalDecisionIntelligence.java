package org.predictiveedge.decision.application;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;

/** Fail-closed final intelligence gate over the complete governed resource bundle. */
public final class GovernedFinalDecisionIntelligence implements FinalDecisionIntelligence {
    private final ShadowRecommendationValidator policy;
    private final String requiredFirstLevelProvider;

    public GovernedFinalDecisionIntelligence(
            ShadowRecommendationValidator policy, String requiredFirstLevelProvider) {
        this.policy = Objects.requireNonNull(policy, "Recommendation policy is required");
        if (requiredFirstLevelProvider == null || requiredFirstLevelProvider.isBlank())
            throw new IllegalArgumentException("Required first-level provider is required");
        this.requiredFirstLevelProvider = requiredFirstLevelProvider.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public RecommendationValidationResult evaluate(
            AITradingDecisionInputBundle bundle, AIRecommendation proposal) {
        Objects.requireNonNull(bundle, "Input bundle is required");
        Objects.requireNonNull(proposal, "First-level proposal is required");
        var reasons = new ArrayList<String>();
        String expectedPrefix = requiredFirstLevelProvider + ":";
        if (!proposal.modelId().toLowerCase(Locale.ROOT).startsWith(expectedPrefix)) {
            reasons.add("FIRST_LEVEL_PROVIDER_NOT_" + requiredFirstLevelProvider.toUpperCase(Locale.ROOT));
        }
        RecommendationValidationResult governed = policy.validate(bundle, proposal);
        if (!governed.accepted()) reasons.addAll(governed.reasons());
        return reasons.isEmpty() ? RecommendationValidationResult.pass()
                : RecommendationValidationResult.rejected(reasons.stream().distinct().toList());
    }
}
