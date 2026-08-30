package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import org.predictiveedge.decision.domain.ModelOutcome;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.RecommendationOutcomeContract;
import org.predictiveedge.decision.domain.ResolvedModelOutcome;
import org.predictiveedge.decision.domain.ShadowCaseStatus;
import org.predictiveedge.decision.domain.ShadowDecisionCase;
import org.predictiveedge.decision.domain.ShadowEvidenceBatch;
import org.predictiveedge.decision.domain.ShadowScope;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcShadowDecisionStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final ShadowScope SCOPE = new ShadowScope(USER, EQUITY);
    private static final String HASH = "c".repeat(64);

    @Test
    void appendsScopeAndDecisionCaseWithoutUpdatingExistingEvidence() throws Exception {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1, 1);
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("{}");
        JdbcShadowDecisionStore store = new JdbcShadowDecisionStore(jdbc, json);

        boolean appended = store.append(decisionCase());

        assertThat(appended).isTrue();
        assertThat(jdbc.sql).hasSize(2);
        assertThat(jdbc.sql.get(0)).contains("insert into decision.shadow_scope");
        assertThat(jdbc.sql.get(1)).contains("insert into decision.shadow_decision_case");
        assertThat(jdbc.sql).allMatch(sql -> !sql.stripLeading().startsWith("update"));
    }

    @Test
    void appendsOnlyOneOutcomePerRecommendation() throws Exception {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1, 0);
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("{}");
        JdbcShadowDecisionStore store = new JdbcShadowDecisionStore(jdbc, json);
        RecommendationOutcomeContract contract = RecommendationOutcomeContract.from(recommendation());
        ResolvedModelOutcome outcome = new ResolvedModelOutcome("recommendation-1", ModelOutcome.WIN,
                BigDecimal.valueOf(1.4), NOW.plusSeconds(130), "POSITIVE_AFTER_COSTS", "path-1", "strict-binary-v1");

        assertThat(store.append(contract, outcome)).isTrue();
        assertThat(store.append(contract, outcome)).isFalse();
        assertThat(jdbc.sql).allMatch(sql -> sql.contains("insert into decision.shadow_model_outcome"));
    }

    @Test
    void appendsOneCompleteEvidenceBatchForTheSingletonScope() throws Exception {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1, 1);
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("{}");
        JdbcShadowEvidenceStore store = new JdbcShadowEvidenceStore(jdbc, json, () -> "bundle-2");
        AITradingDecisionInputBundle bundle = bundle();
        ShadowEvidenceBatch batch = new ShadowEvidenceBatch("batch-1", SCOPE, NOW,
                bundle.evidenceManifest(), bundle.executionContext(), bundle.resources());

        assertThat(store.append(batch)).isTrue();
        assertThat(jdbc.sql).hasSize(2);
        assertThat(jdbc.sql.get(1)).contains("insert into decision.shadow_evidence_batch");
    }

    private static ShadowDecisionCase decisionCase() {
        return new ShadowDecisionCase("case-1", bundle(), recommendation(), ShadowCaseStatus.RECORDED,
                List.of(), NOW);
    }

    private static AITradingDecisionInputBundle bundle() {
        Map<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            resources.put(type, new DecisionResource("resource-" + type, type, USER, EQUITY,
                    AssessmentReadiness.READY, GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3),
                    NOW.minusSeconds(2), NOW.plusSeconds(60), "payload:" + type, HASH));
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

    private static AIRecommendation recommendation() {
        return new AIRecommendation("recommendation-1", "bundle-1", SCOPE, RecommendationAction.BUY,
                NOW, BigDecimal.valueOf(.65), BigDecimal.ONE, BigDecimal.valueOf(99.90),
                BigDecimal.valueOf(100.10), NOW.plusSeconds(20), NOW.plusSeconds(120),
                BigDecimal.valueOf(98.50), BigDecimal.valueOf(103), "model-v1",
                "Breakout evidence exceeds the configured threshold", List.of("resource-CHART"));
    }

    private static final class ScriptedJdbcTemplate extends JdbcTemplate {
        private final Queue<Integer> results = new ArrayDeque<>();
        private final List<String> sql = new java.util.ArrayList<>();

        private ScriptedJdbcTemplate(Integer... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql.add(sql);
            return results.remove();
        }
    }
}
