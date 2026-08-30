package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.ChartBias;
import org.predictiveedge.chart.domain.ChartIntelligenceEngine;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.chart.domain.ChartTrigger;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.IntelligenceModule;
import org.predictiveedge.decision.domain.RecommendationAction;

class ChartFeedbackFactoryTest {
    private static final Instant NOW = Instant.parse("2026-08-09T05:00:00Z");
    private final ChartFeedbackFactory factory = new ChartFeedbackFactory(new ChartIntelligenceEngine());

    @Test
    void publishesLineageBearingChartFeedbackForTheDecisionSystem() {
        ChartSnapshot snapshot = new ChartSnapshot("chart-feedback-1", "nse", "ine002a01018",
                ChartBias.BULLISH, ChartBias.NEUTRAL, ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT,
                true, true, true, 84, ChartReadiness.READY, true, NOW.minusSeconds(30),
                NOW.minusSeconds(20), NOW.minusSeconds(10), NOW.plusSeconds(60), "b".repeat(64),
                List.of("chart-context:42", "feature-manifest:84"));

        var feedback = factory.create(snapshot);

        assertThat(feedback.module()).isEqualTo(IntelligenceModule.CHART);
        assertThat(feedback.proposedAction()).isEqualTo(RecommendationAction.BUY);
        assertThat(feedback.confidence()).isEqualTo(84);
        assertThat(feedback.instrument().venue()).isEqualTo("NSE");
        assertThat(feedback.reasons()).containsExactly("SIGNAL_CONFIRMED");
        assertThat(feedback.evidenceReferences()).containsExactly("chart-context:42", "feature-manifest:84");
    }

    @Test
    void preservesStaleReadinessSoCoordinatorCannotUseTheFeedback() {
        ChartSnapshot stale = new ChartSnapshot("chart-feedback-2", "NSE", "INE002A01018",
                ChartBias.BULLISH, ChartBias.BULLISH, ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT,
                true, true, true, 84, ChartReadiness.STALE, true, NOW.minusSeconds(30),
                NOW.minusSeconds(20), NOW.minusSeconds(10), NOW.plusSeconds(60), "c".repeat(64), List.of());

        var feedback = factory.create(stale);

        assertThat(feedback.proposedAction()).isEqualTo(RecommendationAction.WAIT);
        assertThat(feedback.readiness()).isEqualTo(AssessmentReadiness.STALE);
        assertThat(feedback.gateDisposition()).isEqualTo(GateDisposition.NOT_APPLICABLE);
    }
}
