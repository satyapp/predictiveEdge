package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.application.AiModelEvidenceInput;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.ShadowScope;

class OpenAiRecommendationGatewayTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final ShadowScope SCOPE = new ShadowScope(USER, new InstrumentRef("NSE", "INE002A01018"));
    private static final String HASH = "f".repeat(64);

    @Test
    void sendsStrictSchemaAndMapsAConservativeWaitResponse() {
        AtomicReference<String> request = new AtomicReference<>();
        OpenAiResponsesTransport transport = (apiKey, body) -> {
            request.set(body);
            return response("""
                    {"action":"WAIT","calibrated_win_probability":0.0,"expected_value_after_costs":0,
                     "entry_price_low":null,"entry_price_high":null,"entry_valid_for_seconds":null,
                     "evaluation_horizon_seconds":null,"stop_loss":null,"target":null,
                     "rationale":"Payload references do not expose sufficient factual evidence.",
                     "evidence_references":["resource-MARKET","resource-RISK","resource-PORTFOLIO"]}
                    """);
        };
        var gateway = new OpenAiRecommendationGateway(json(), transport, "openai", "test-key", "gpt-test", input(),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "recommendation-1");

        var recommendation = gateway.recommend(bundle(AssessmentReadiness.READY));

        assertThat(recommendation.action()).isEqualTo(RecommendationAction.WAIT);
        assertThat(recommendation.modelId()).isEqualTo("openai:gpt-test");
        assertThat(request.get()).contains("json_schema", "strict", "bundle-1", "Never claim to execute");
        assertThat(request.get()).doesNotContain("test-key");
    }

    @Test
    void refusesToCallOpenAiForAnUnreadyBundle() {
        var gateway = new OpenAiRecommendationGateway(json(), (key, request) -> {
            throw new AssertionError("Transport must not be called");
        }, "openai", "test-key", "gpt-test", input(), Clock.fixed(NOW, ZoneOffset.UTC), () -> "recommendation-1");

        assertThatThrownBy(() -> gateway.recommend(bundle(AssessmentReadiness.STALE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unready");
    }

    private static String response(String structuredJson) {
        try {
            String escaped = json().writeValueAsString(structuredJson);
            return "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                    + escaped + "}]}]}";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static AITradingDecisionInputBundle bundle(AssessmentReadiness readiness) {
        Map<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            resources.put(type, new DecisionResource("resource-" + type, type, USER, SCOPE.instrument(), readiness,
                    GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3), NOW.minusSeconds(2),
                    NOW.plusSeconds(60), "payload:" + type, HASH));
        }
        var manifest = new PointInTimeEvidenceManifest("manifest-1", NOW.minusSeconds(4), NOW.minusSeconds(3),
                "zerodha-v3", "feature-v1", "adjustment-v1", "instrument-v1", AssessmentReadiness.READY,
                AssessmentReadiness.READY, true, List.of("bar-1", "depth-1"), HASH);
        var execution = new ExecutionContext(NOW.minusSeconds(1), money("99.95"), money("100.05"), "depth-1",
                1, money("100.05"), BigDecimal.TEN, BigDecimal.ONE, money("0.25"), money("0.5"), 250,
                true, true, NOW.plusSeconds(30));
        return new AITradingDecisionInputBundle("bundle-1", SCOPE, "intent-1", NOW, manifest, execution, resources);
    }

    static ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    static org.predictiveedge.decision.application.AiEvidencePayloadQuery input() {
        return bundle -> {
            Map<DecisionResourceType, String> payloads = new EnumMap<>(DecisionResourceType.class);
            for (DecisionResourceType type : DecisionResourceType.values()) {
                payloads.put(type, "{\"resource_type\":\"" + type + "\",\"facts\":\"synthetic-test-only\"}");
            }
            return new AiModelEvidenceInput(bundle, payloads);
        };
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
}
