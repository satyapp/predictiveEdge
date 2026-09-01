package org.predictiveedge.decision.infrastructure;

import java.time.Instant;
import java.util.Objects;
import org.predictiveedge.decision.application.DecisionResourceQuery;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.marketintelligence.application.MarketContextQueryPort;
import org.predictiveedge.marketintelligence.domain.ContextScopeType;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketContextKey;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;
import org.predictiveedge.marketintelligence.domain.QualityDisposition;

/** Translates the governed semantic Market Context; raw OHLCV alone never marks MARKET ready. */
public final class MarketContextDecisionResourceQuery implements DecisionResourceQuery {
    private final MarketContextQueryPort marketContexts;
    private final String horizon;
    private final ExactAiPayloadPublisher payloads;

    public MarketContextDecisionResourceQuery(MarketContextQueryPort marketContexts, String horizon) {
        this(marketContexts, horizon, null);
    }

    public MarketContextDecisionResourceQuery(
            MarketContextQueryPort marketContexts, String horizon, ExactAiPayloadPublisher payloads) {
        this.marketContexts = Objects.requireNonNull(marketContexts, "Market Context query is required");
        this.horizon = required(horizon, "Market Context horizon");
        this.payloads = payloads;
    }

    @Override
    public DecisionResourceType type() {
        return DecisionResourceType.MARKET;
    }

    @Override
    public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(cutoff, "Evidence cutoff is required");
        MarketContextKey key = new MarketContextKey(ContextScopeType.INSTRUMENT,
                scope.instrument().instrumentId(), horizon);
        return marketContexts.findLatest(scope.userId(), key, new EvaluationCutoff(cutoff, cutoff))
                .map(context -> publish(scope, cutoff, key, context))
                .orElseGet(() -> unavailable(scope, cutoff, key));
    }

    private DecisionResource publish(
            ShadowScope scope, Instant cutoff, MarketContextKey key, MarketContextSnapshot context) {
        DecisionResource resource = map(scope, cutoff, key, context);
        return payloads == null ? resource : payloads.publish(scope, type(), resource, context);
    }

    private static DecisionResource map(
            ShadowScope scope, Instant cutoff, MarketContextKey key, MarketContextSnapshot context) {
        if (!context.key().equals(key)) {
            throw new IllegalArgumentException("Market Context does not match the fixed shadow equity and horizon");
        }
        AssessmentReadiness readiness = readiness(context, cutoff);
        String reference = "market-context:" + key.scopeType() + ":" + key.scopeId() + ":" + key.horizon()
                + ":" + context.semanticHash().value();
        return new DecisionResource(reference, DecisionResourceType.MARKET, scope.userId(), scope.instrument(),
                readiness, GateDisposition.PASS, context.cutoff().analysisCutoff(),
                context.cutoff().knowledgeCutoff(), context.decisionReadyAt(), context.expiresAt(), reference,
                context.semanticHash().value());
    }

    private static AssessmentReadiness readiness(MarketContextSnapshot context, Instant cutoff) {
        if (!cutoff.isBefore(context.expiresAt())) return AssessmentReadiness.STALE;
        if (context.quality().disposition() == QualityDisposition.PASS) return AssessmentReadiness.READY;
        if (context.quality().disposition() == QualityDisposition.DEGRADED) return AssessmentReadiness.WARMUP;
        return AssessmentReadiness.INVALID;
    }

    private static DecisionResource unavailable(ShadowScope scope, Instant cutoff, MarketContextKey key) {
        String reference = "unavailable:market-context:" + key.scopeType() + ":" + key.scopeId() + ":"
                + key.horizon() + ":" + cutoff;
        return new DecisionResource("market:unavailable:" + cutoff, DecisionResourceType.MARKET,
                scope.userId(), scope.instrument(), AssessmentReadiness.UNAVAILABLE, GateDisposition.NOT_APPLICABLE,
                cutoff, cutoff, cutoff, cutoff.plusNanos(1), reference, EvidenceHashing.sha256(reference));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
