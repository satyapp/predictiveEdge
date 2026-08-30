package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.ChartBias;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.chart.domain.ChartTrigger;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.ContextScopeType;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketContextKey;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;
import org.predictiveedge.marketintelligence.domain.QualityAssessment;
import org.predictiveedge.marketintelligence.domain.QualityDisposition;

class DecisionResourceQueryAdaptersTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ShadowScope SCOPE = new ShadowScope(USER, new InstrumentRef("NSE", "INE002A01018"));

    @Test
    void translatesAChartSnapshotAndHashesTheCompleteAssessment() {
        ChartSnapshot snapshot = new ChartSnapshot("chart-1", "NSE", "INE002A01018",
                ChartBias.BULLISH, ChartBias.NEUTRAL, ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT,
                true, true, true, 82, ChartReadiness.READY, true, NOW.minusSeconds(30),
                NOW.minusSeconds(20), NOW.minusSeconds(10), NOW.plusSeconds(30), "b".repeat(64),
                List.of("bar:1", "feature:2"));
        var query = new ChartDecisionResourceQuery((user, venue, instrument, cutoff) -> Optional.of(snapshot));

        var resource = query.findLatest(SCOPE, NOW);

        assertThat(resource.type()).isEqualTo(DecisionResourceType.CHART);
        assertThat(resource.readiness()).isEqualTo(AssessmentReadiness.READY);
        assertThat(resource.payloadRef()).isEqualTo("chart-snapshot:chart-1");
        assertThat(resource.evidenceHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void makesMissingChartEvidenceExplicitAndUnusable() {
        var query = new ChartDecisionResourceQuery((user, venue, instrument, cutoff) -> Optional.empty());

        var resource = query.findLatest(SCOPE, NOW);

        assertThat(resource.readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(resource.isUsableAt(NOW)).isFalse();
        assertThat(resource.payloadRef()).startsWith("unavailable:chart-snapshot:");
    }

    @Test
    void readsTheLatestSemanticMarketContextAndPreservesItsHash() {
        MarketContextKey key = new MarketContextKey(ContextScopeType.INSTRUMENT, "INE002A01018", "INTRADAY");
        MarketContextSnapshot context = mock(MarketContextSnapshot.class);
        QualityAssessment quality = mock(QualityAssessment.class);
        when(context.key()).thenReturn(key);
        when(context.cutoff()).thenReturn(new EvaluationCutoff(NOW.minusSeconds(30), NOW.minusSeconds(20)));
        when(context.decisionReadyAt()).thenReturn(NOW.minusSeconds(10));
        when(context.expiresAt()).thenReturn(NOW.plusSeconds(30));
        when(context.quality()).thenReturn(quality);
        when(quality.disposition()).thenReturn(QualityDisposition.PASS);
        when(context.semanticHash()).thenReturn(new ContentHash("c".repeat(64)));
        var query = new MarketContextDecisionResourceQuery((user, requestedKey, cutoff) -> Optional.of(context),
                "INTRADAY");

        var resource = query.findLatest(SCOPE, NOW);

        assertThat(resource.type()).isEqualTo(DecisionResourceType.MARKET);
        assertThat(resource.readiness()).isEqualTo(AssessmentReadiness.READY);
        assertThat(resource.payloadRef()).startsWith("market-context:INSTRUMENT:INE002A01018:INTRADAY");
        assertThat(resource.evidenceHash()).isEqualTo("c".repeat(64));
    }

    @Test
    void makesMissingMarketEvidenceExplicitAndUnusable() {
        var query = new MarketContextDecisionResourceQuery((user, key, cutoff) -> Optional.empty(), "INTRADAY");

        var resource = query.findLatest(SCOPE, NOW);

        assertThat(resource.readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(resource.isUsableAt(NOW)).isFalse();
        assertThat(resource.payloadRef()).startsWith("unavailable:market-context:");
    }
}
