package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;

/** Mandatory final authority that may approve or reject a first-level model proposal. */
@FunctionalInterface
public interface FinalDecisionIntelligence {
    RecommendationValidationResult evaluate(
            AITradingDecisionInputBundle inputBundle, AIRecommendation firstLevelProposal);
}
