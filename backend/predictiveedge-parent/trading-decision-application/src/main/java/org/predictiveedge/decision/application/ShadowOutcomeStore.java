package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.RecommendationOutcomeContract;
import org.predictiveedge.decision.domain.ResolvedModelOutcome;

@FunctionalInterface
public interface ShadowOutcomeStore {
    boolean append(RecommendationOutcomeContract contract, ResolvedModelOutcome outcome);
}
