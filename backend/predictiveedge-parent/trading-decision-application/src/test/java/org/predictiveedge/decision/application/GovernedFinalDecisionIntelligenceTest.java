package org.predictiveedge.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.predictiveedge.decision.domain.ShadowScope;

class GovernedFinalDecisionIntelligenceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T04:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final InstrumentRef INSTRUMENT = new InstrumentRef("NSE", "SBIN");
    private static final ShadowScope SCOPE = new ShadowScope(USER, INSTRUMENT);
    private static final String HASH = "a".repeat(64);

    @Test
    void acceptsOnlyOllamaProposalAfterAllGovernedResourcesPass() {
        var intelligence = new GovernedFinalDecisionIntelligence(
                new ShadowRecommendationValidator(new BigDecimal("0.55")), "ollama");

        assertThat(intelligence.evaluate(bundle(), proposal("ollama:qwen3:8b")).accepted()).isTrue();
        assertThat(intelligence.evaluate(bundle(), proposal("openai:gpt-5.4-mini")).reasons())
                .contains("FIRST_LEVEL_PROVIDER_NOT_OLLAMA");
    }

    private static AITradingDecisionInputBundle bundle() {
        Map<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            resources.put(type, new DecisionResource("resource-" + type, type, USER, INSTRUMENT,
                    AssessmentReadiness.READY, GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3),
                    NOW.minusSeconds(2), NOW.plusSeconds(60), "payload:" + type, HASH));
        }
        var manifest = new PointInTimeEvidenceManifest("manifest-1", NOW.minusSeconds(4), NOW.minusSeconds(3),
                "zerodha-v3", "feature-v1", "adjustment-v1", "instrument-v1",
                AssessmentReadiness.READY, AssessmentReadiness.READY, true, List.of("bar-1"), HASH);
        var execution = new ExecutionContext(NOW.minusSeconds(1), new BigDecimal("99.95"),
                new BigDecimal("100.05"), "depth-1", 1, new BigDecimal("100.05"), BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("0.25"), new BigDecimal("0.5"), 250,
                true, true, NOW.plusSeconds(30));
        return new AITradingDecisionInputBundle("bundle-1", SCOPE, "intent-1", NOW,
                manifest, execution, resources);
    }

    private static AIRecommendation proposal(String model) {
        return new AIRecommendation("recommendation-1", "bundle-1", SCOPE, RecommendationAction.BUY,
                NOW, new BigDecimal("0.65"), BigDecimal.ONE, new BigDecimal("99.90"),
                new BigDecimal("100.10"), NOW.plusSeconds(20), NOW.plusSeconds(120),
                new BigDecimal("98.50"), new BigDecimal("103"), model,
                "First-level proposal", List.of("resource-CHART"));
    }
}
