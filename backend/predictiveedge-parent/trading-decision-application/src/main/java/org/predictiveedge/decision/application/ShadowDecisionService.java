package org.predictiveedge.decision.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.ShadowCaseStatus;
import org.predictiveedge.decision.domain.ShadowDecisionCase;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.decision.domain.TraderIntent;

/** Two-stage shadow orchestrator: first-level model proposal, then mandatory final intelligence. */
public final class ShadowDecisionService {
    private final ShadowScope configuredScope;
    private final ShadowDecisionInputQuery inputQuery;
    private final AiRecommendationGateway firstLevelGateway;
    private final FinalDecisionIntelligence finalIntelligence;
    private final ShadowDecisionCaseStore caseStore;
    private final Clock clock;
    private final Supplier<String> caseIds;

    public ShadowDecisionService(ShadowScope configuredScope, ShadowDecisionInputQuery inputQuery,
            AiRecommendationGateway firstLevelGateway, FinalDecisionIntelligence finalIntelligence,
            ShadowDecisionCaseStore caseStore, Clock clock, Supplier<String> caseIds) {
        this.configuredScope = Objects.requireNonNull(configuredScope, "Configured shadow scope is required");
        this.inputQuery = Objects.requireNonNull(inputQuery, "Input query is required");
        this.firstLevelGateway = Objects.requireNonNull(firstLevelGateway, "First-level AI gateway is required");
        this.finalIntelligence = Objects.requireNonNull(finalIntelligence, "Final decision intelligence is required");
        this.caseStore = Objects.requireNonNull(caseStore, "Case store is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.caseIds = Objects.requireNonNull(caseIds, "Case id supplier is required");
    }

    public ShadowDecisionCase evaluate(TraderIntent traderIntent) {
        Objects.requireNonNull(traderIntent, "Trader intent is required");
        configuredScope.requireMatches(traderIntent.traderId(), traderIntent.instrument());
        Instant cutoff = clock.instant();
        if (!traderIntent.isActiveAt(cutoff)) throw new IllegalArgumentException("Trader intent is not active at cutoff");
        AITradingDecisionInputBundle bundle = Objects.requireNonNull(
                inputQuery.assemble(configuredScope, traderIntent, cutoff), "Input query returned no bundle");
        if (!bundle.scope().equals(configuredScope) || !bundle.traderIntentId().equals(traderIntent.intentId())) {
            throw new IllegalArgumentException("Assembled bundle does not match configured scope and trader intent");
        }

        String caseId = required(caseIds.get(), "Case id");
        ShadowDecisionCase decisionCase;
        if (!bundle.isReady()) {
            decisionCase = new ShadowDecisionCase(caseId, bundle, null, ShadowCaseStatus.BLOCKED_INPUT,
                    List.of("INPUT_BUNDLE_NOT_READY"), cutoff);
        } else {
            AIRecommendation recommendation = Objects.requireNonNull(
                    firstLevelGateway.recommend(bundle), "First-level AI gateway returned no proposal");
            RecommendationValidationResult validation = finalIntelligence.evaluate(bundle, recommendation);
            if (validation == null) throw new IllegalStateException("Final decision intelligence returned no result");
            decisionCase = new ShadowDecisionCase(caseId, bundle, recommendation,
                    validation.accepted() ? ShadowCaseStatus.RECORDED : ShadowCaseStatus.REJECTED_POLICY,
                    validation.reasons(), cutoff);
        }
        if (!caseStore.append(decisionCase)) throw new IllegalStateException("Shadow decision case already exists");
        return decisionCase;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
