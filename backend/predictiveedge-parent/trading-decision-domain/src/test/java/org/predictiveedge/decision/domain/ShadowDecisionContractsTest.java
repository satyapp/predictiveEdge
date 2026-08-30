package org.predictiveedge.decision.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShadowDecisionContractsTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final String HASH = "a".repeat(64);

    @Test
    void bundleRequiresAllTwelveResourcesForTheConfiguredScope() {
        ShadowScope scope = new ShadowScope(USER, EQUITY);
        Map<DecisionResourceType, DecisionResource> resources = readyResources(scope);
        resources.remove(DecisionResourceType.LEARNING);

        assertThatThrownBy(() -> new AITradingDecisionInputBundle("bundle-1", scope, "intent-1", NOW,
                manifest(), execution(), resources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twelve");
    }

    @Test
    void bundleRejectsAResourceFromAnotherUser() {
        ShadowScope scope = new ShadowScope(USER, EQUITY);
        Map<DecisionResourceType, DecisionResource> resources = readyResources(scope);
        resources.put(DecisionResourceType.RISK, resource(DecisionResourceType.RISK,
                new ShadowScope(UUID.fromString("20000000-0000-0000-0000-000000000002"), EQUITY),
                AssessmentReadiness.READY));

        assertThatThrownBy(() -> new AITradingDecisionInputBundle("bundle-1", scope, "intent-1", NOW,
                manifest(), execution(), resources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one-user/one-equity");
    }

    @Test
    void strictResolverCountsBreakevenAndMissingEntryAsLosses() {
        AIRecommendation recommendation = recommendation(BigDecimal.valueOf(1.25));
        RecommendationOutcomeContract contract = RecommendationOutcomeContract.from(recommendation);
        RecommendationOutcomeResolver resolver = new RecommendationOutcomeResolver();

        ResolvedModelOutcome breakeven = resolver.resolve(contract,
                new OutcomeObservation(true, false, BigDecimal.ZERO, NOW.plusSeconds(130), "path-1"));
        ResolvedModelOutcome missingEntry = resolver.resolve(contract,
                new OutcomeObservation(false, false, BigDecimal.ONE, NOW.plusSeconds(130), "path-2"));

        assertThat(breakeven.outcome()).isEqualTo(ModelOutcome.LOSS);
        assertThat(breakeven.resolutionReason()).isEqualTo("ZERO_OR_NEGATIVE_AFTER_COSTS");
        assertThat(missingEntry.outcome()).isEqualTo(ModelOutcome.LOSS);
        assertThat(missingEntry.resolutionReason()).isEqualTo("ENTRY_NOT_VALID");
    }

    @Test
    void positiveAfterCostsWithoutStopIsAWin() {
        RecommendationOutcomeContract contract = RecommendationOutcomeContract.from(
                recommendation(BigDecimal.valueOf(2.40)));

        ResolvedModelOutcome outcome = new RecommendationOutcomeResolver().resolve(contract,
                new OutcomeObservation(true, false, BigDecimal.valueOf(2.40), NOW.plusSeconds(130), "path-3"));

        assertThat(outcome.outcome()).isEqualTo(ModelOutcome.WIN);
        assertThat(outcome.resolutionReason()).isEqualTo("POSITIVE_AFTER_COSTS");
    }

    private static PointInTimeEvidenceManifest manifest() {
        return new PointInTimeEvidenceManifest("manifest-1", NOW.minusSeconds(4), NOW.minusSeconds(3),
                "zerodha-v3", "feature-v1", "adjustment-v1", "instrument-v1",
                AssessmentReadiness.READY, AssessmentReadiness.READY, true, List.of("bar-1", "depth-1"), HASH);
    }

    private static ExecutionContext execution() {
        return new ExecutionContext(NOW.minusSeconds(1), BigDecimal.valueOf(99.95), BigDecimal.valueOf(100.05),
                "depth-1", 1, BigDecimal.valueOf(100.05), BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.valueOf(.25), BigDecimal.valueOf(.5), 250, true, true, NOW.plusSeconds(30));
    }

    private static Map<DecisionResourceType, DecisionResource> readyResources(ShadowScope scope) {
        Map<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            resources.put(type, resource(type, scope, AssessmentReadiness.READY));
        }
        return resources;
    }

    private static DecisionResource resource(
            DecisionResourceType type, ShadowScope scope, AssessmentReadiness readiness) {
        return new DecisionResource("resource-" + type, type, scope.userId(), scope.instrument(), readiness,
                GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3), NOW.minusSeconds(2),
                NOW.plusSeconds(60), "payload:" + type, HASH);
    }

    private static AIRecommendation recommendation(BigDecimal expectedValue) {
        return new AIRecommendation("recommendation-1", "bundle-1", new ShadowScope(USER, EQUITY),
                RecommendationAction.BUY, NOW, BigDecimal.valueOf(.65), expectedValue,
                BigDecimal.valueOf(99.90), BigDecimal.valueOf(100.10), NOW.plusSeconds(20),
                NOW.plusSeconds(120), BigDecimal.valueOf(98.50), BigDecimal.valueOf(103),
                "model-v1", "Breakout evidence exceeds the configured threshold", List.of("resource-CHART"));
    }
}
