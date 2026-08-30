package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;

/** Sole recommendation-origin port. Implementations call an AI model and return a structured response. */
@FunctionalInterface
public interface AiRecommendationGateway {
    AIRecommendation recommend(AITradingDecisionInputBundle inputBundle);
}
