package org.predictiveedge.decision.application;

import java.util.Objects;
import org.predictiveedge.decision.domain.OutcomeObservation;
import org.predictiveedge.decision.domain.RecommendationOutcomeContract;
import org.predictiveedge.decision.domain.RecommendationOutcomeResolver;
import org.predictiveedge.decision.domain.ResolvedModelOutcome;

/** Resolves and appends the strict model outcome independently of trader execution. */
public final class ShadowOutcomeService {
    private final RecommendationOutcomeResolver resolver;
    private final ShadowOutcomeStore outcomeStore;

    public ShadowOutcomeService(RecommendationOutcomeResolver resolver, ShadowOutcomeStore outcomeStore) {
        this.resolver = Objects.requireNonNull(resolver, "Outcome resolver is required");
        this.outcomeStore = Objects.requireNonNull(outcomeStore, "Outcome store is required");
    }

    public ResolvedModelOutcome resolve(
            RecommendationOutcomeContract contract, OutcomeObservation observation) {
        ResolvedModelOutcome outcome = resolver.resolve(contract, observation);
        if (!outcomeStore.append(contract, outcome)) throw new IllegalStateException("Recommendation outcome already exists");
        return outcome;
    }
}
