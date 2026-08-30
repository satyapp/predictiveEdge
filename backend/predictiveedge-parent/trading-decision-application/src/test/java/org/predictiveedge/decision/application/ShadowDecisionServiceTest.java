package org.predictiveedge.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.ShadowCaseStatus;
import org.predictiveedge.decision.domain.ShadowDecisionCase;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.decision.domain.TraderIntent;

class ShadowDecisionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final ShadowScope SCOPE = new ShadowScope(USER, EQUITY);
    private static final String HASH = "b".repeat(64);

    @Test
    void recordsAValidatedAiRecommendationWithoutAnyBrokerDependency() {
        AtomicReference<ShadowDecisionCase> stored = new AtomicReference<>();
        ShadowDecisionService service = service(readyBundle(), bundle -> recommendation(BigDecimal.ONE),
                decisionCase -> stored.compareAndSet(null, decisionCase));

        ShadowDecisionCase result = service.evaluate(intent(USER, EQUITY));

        assertThat(result.status()).isEqualTo(ShadowCaseStatus.RECORDED);
        assertThat(result.recommendation().action()).isEqualTo(RecommendationAction.BUY);
        assertThat(stored.get()).isEqualTo(result);
    }

    @Test
    void blocksBeforeCallingAiWhenAnyMandatoryInputIsNotReady() {
        AtomicBoolean aiCalled = new AtomicBoolean();
        AITradingDecisionInputBundle stale = bundleWith(DecisionResourceType.DATA_QUALITY, AssessmentReadiness.STALE);
        ShadowDecisionService service = service(stale, bundle -> {
            aiCalled.set(true);
            return recommendation(BigDecimal.ONE);
        }, decisionCase -> true);

        ShadowDecisionCase result = service.evaluate(intent(USER, EQUITY));

        assertThat(result.status()).isEqualTo(ShadowCaseStatus.BLOCKED_INPUT);
        assertThat(result.recommendation()).isNull();
        assertThat(aiCalled).isFalse();
    }

    @Test
    void persistsButRejectsANonPositiveEdgeRecommendation() {
        ShadowDecisionService service = service(readyBundle(), bundle -> recommendation(BigDecimal.ZERO),
                decisionCase -> true);

        ShadowDecisionCase result = service.evaluate(intent(USER, EQUITY));

        assertThat(result.status()).isEqualTo(ShadowCaseStatus.REJECTED_POLICY);
        assertThat(result.policyReasons()).contains("NON_POSITIVE_EXPECTED_VALUE");
    }

    @Test
    void refusesAnyOtherUserOrEquity() {
        ShadowDecisionService service = service(readyBundle(), bundle -> recommendation(BigDecimal.ONE),
                decisionCase -> true);

        assertThatThrownBy(() -> service.evaluate(intent(UUID.randomUUID(), EQUITY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one-user/one-equity");
    }

    private static ShadowDecisionService service(AITradingDecisionInputBundle bundle,
            AiRecommendationGateway gateway, ShadowDecisionCaseStore store) {
        return new ShadowDecisionService(SCOPE, (scope, intent, cutoff) -> bundle, gateway,
                new ShadowRecommendationValidator(BigDecimal.valueOf(.55)), store,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "case-1");
    }

    private static TraderIntent intent(UUID user, InstrumentRef instrument) {
        return new TraderIntent("intent-1", user, instrument, EnumSet.of(RecommendationAction.BUY),
                "breakout-v1", NOW.minusSeconds(10), NOW.plusSeconds(60));
    }

    private static AITradingDecisionInputBundle readyBundle() {
        return bundleWith(null, null);
    }

    private static AITradingDecisionInputBundle bundleWith(
            DecisionResourceType changedType, AssessmentReadiness changedReadiness) {
        Map<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            AssessmentReadiness readiness = type == changedType ? changedReadiness : AssessmentReadiness.READY;
            resources.put(type, new DecisionResource("resource-" + type, type, USER, EQUITY, readiness,
                    GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3), NOW.minusSeconds(2),
                    NOW.plusSeconds(60), "payload:" + type, HASH));
        }
        PointInTimeEvidenceManifest manifest = new PointInTimeEvidenceManifest("manifest-1",
                NOW.minusSeconds(4), NOW.minusSeconds(3), "zerodha-v3", "feature-v1", "adjustment-v1",
                "instrument-v1", AssessmentReadiness.READY, AssessmentReadiness.READY, true,
                List.of("bar-1", "depth-1"), HASH);
        ExecutionContext execution = new ExecutionContext(NOW.minusSeconds(1), BigDecimal.valueOf(99.95),
                BigDecimal.valueOf(100.05), "depth-1", 1, BigDecimal.valueOf(100.05), BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.valueOf(.25), BigDecimal.valueOf(.5), 250,
                true, true, NOW.plusSeconds(30));
        return new AITradingDecisionInputBundle("bundle-1", SCOPE, "intent-1", NOW, manifest, execution, resources);
    }

    private static AIRecommendation recommendation(BigDecimal expectedValue) {
        return new AIRecommendation("recommendation-1", "bundle-1", SCOPE, RecommendationAction.BUY,
                NOW, BigDecimal.valueOf(.65), expectedValue, BigDecimal.valueOf(99.90),
                BigDecimal.valueOf(100.10), NOW.plusSeconds(20), NOW.plusSeconds(120),
                BigDecimal.valueOf(98.50), BigDecimal.valueOf(103), "model-v1",
                "Breakout evidence exceeds the configured threshold", List.of("resource-CHART"));
    }
}
