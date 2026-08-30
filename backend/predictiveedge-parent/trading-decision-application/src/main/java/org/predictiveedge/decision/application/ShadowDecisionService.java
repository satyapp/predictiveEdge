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

/** One-user/one-equity shadow orchestrator. No broker execution port exists by design. */
public final class ShadowDecisionService {
    private final ShadowScope configuredScope;
    private final ShadowDecisionInputQuery inputQuery;
    private final AiRecommendationGateway aiGateway;
    private final ShadowRecommendationValidator validator;
    private final ShadowDecisionCaseStore caseStore;
    private final Clock clock;
    private final Supplier<String> caseIds;

    public ShadowDecisionService(ShadowScope configuredScope, ShadowDecisionInputQuery inputQuery,
            AiRecommendationGateway aiGateway, ShadowRecommendationValidator validator,
            ShadowDecisionCaseStore caseStore, Clock clock, Supplier<String> caseIds) {
        this.configuredScope = Objects.requireNonNull(configuredScope, "Configured shadow scope is required");
        this.inputQuery = Objects.requireNonNull(inputQuery, "Input query is required");
        this.aiGateway = Objects.requireNonNull(aiGateway, "AI gateway is required");
        this.validator = Objects.requireNonNull(validator, "Recommendation validator is required");
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
                    aiGateway.recommend(bundle), "AI gateway returned no recommendation");
            RecommendationValidationResult validation = validator.validate(bundle, recommendation);
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
