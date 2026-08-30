package org.predictiveedge.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.ShadowEvidenceBatch;
import org.predictiveedge.decision.domain.ShadowScope;

class ShadowEvidenceBatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final ShadowScope SCOPE = new ShadowScope(USER, EQUITY);
    private static final String HASH = "a".repeat(64);

    @Test
    void capturesAndAppendsExactlyOneCompleteCausalBatch() {
        AtomicReference<ShadowEvidenceBatch> stored = new AtomicReference<>();
        ShadowEvidenceBatchService service = service(queries(NOW.minusSeconds(2)),
                batch -> stored.compareAndSet(null, batch));

        ShadowEvidenceBatch result = service.capture(NOW);

        assertThat(result.batchId()).isEqualTo("batch-1");
        assertThat(result.scope()).isEqualTo(SCOPE);
        assertThat(result.resources()).hasSize(DecisionResourceType.values().length);
        assertThat(stored.get()).isEqualTo(result);
    }

    @Test
    void requiresExactlyOneContributorForEveryIntelligenceType() {
        List<DecisionResourceQuery> incomplete = queries(NOW.minusSeconds(2)).stream()
                .filter(query -> query.type() != DecisionResourceType.PORTFOLIO)
                .toList();

        assertThatThrownBy(() -> service(incomplete, batch -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every decision resource type");
    }

    @Test
    void rejectsDuplicateContributors() {
        List<DecisionResourceQuery> duplicated = new java.util.ArrayList<>(queries(NOW.minusSeconds(2)));
        duplicated.add(query(DecisionResourceType.MARKET, NOW.minusSeconds(2)));

        assertThatThrownBy(() -> service(duplicated, batch -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsEvidenceThatWasNotAvailableAtTheCutoff() {
        List<DecisionResourceQuery> queries = new java.util.ArrayList<>(queries(NOW.minusSeconds(2)));
        queries.removeIf(query -> query.type() == DecisionResourceType.MARKET);
        queries.add(query(DecisionResourceType.MARKET, NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service(queries, batch -> true).capture(NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
    }

    private static ShadowEvidenceBatchService service(
            List<DecisionResourceQuery> queries, ShadowEvidenceBatchStore store) {
        EvidenceManifestQuery manifestQuery = (scope, cutoff, resources) -> new PointInTimeEvidenceManifest(
                "manifest-1", NOW.minusSeconds(4), NOW.minusSeconds(1), "zerodha-v3", "feature-v1",
                "adjustment-v1", "instrument-v1", AssessmentReadiness.READY, AssessmentReadiness.READY,
                true, List.of("bar-1", "depth-1"), HASH);
        ExecutionContextQuery executionQuery = (scope, cutoff) -> new ExecutionContext(
                NOW.minusSeconds(1), BigDecimal.valueOf(99.95), BigDecimal.valueOf(100.05), "depth-1", 1,
                BigDecimal.valueOf(100.05), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(.25),
                BigDecimal.valueOf(.5), 250, true, true, NOW.plusSeconds(30));
        return new ShadowEvidenceBatchService(SCOPE, queries, manifestQuery, executionQuery, store,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "batch-1");
    }

    private static List<DecisionResourceQuery> queries(Instant availableAt) {
        return Arrays.stream(DecisionResourceType.values()).map(type -> query(type, availableAt)).toList();
    }

    private static DecisionResourceQuery query(DecisionResourceType type, Instant availableAt) {
        return new DecisionResourceQuery() {
            @Override public DecisionResourceType type() { return type; }

            @Override
            public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
                return new DecisionResource("resource-" + type, type, USER, EQUITY, AssessmentReadiness.READY,
                        GateDisposition.PASS, NOW.minusSeconds(4), NOW.minusSeconds(3), availableAt,
                        NOW.plusSeconds(60), "payload:" + type, HASH);
            }
        };
    }
}
