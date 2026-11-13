package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;

/** First-level analysis port. Its structured proposal is never final until Decision Intelligence approves it. */
@FunctionalInterface
public interface AiRecommendationGateway {
    AIRecommendation recommend(AITradingDecisionInputBundle inputBundle);
}
