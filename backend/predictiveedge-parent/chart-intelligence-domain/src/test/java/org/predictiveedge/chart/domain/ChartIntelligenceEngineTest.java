package org.predictiveedge.chart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChartIntelligenceEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-09T05:00:00Z");
    private final ChartIntelligenceEngine engine = new ChartIntelligenceEngine();

    @Test
    void confirmsFinalBullishBreakoutWhenIndependentChartDimensionsAlign() {
        ChartAssessment result = engine.assess(snapshot(ChartBias.BULLISH, ChartBias.BULLISH,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, true,
                ChartReadiness.READY, true));

        assertThat(result.action()).isEqualTo(ChartAction.BUY);
        assertThat(result.confidence()).isEqualTo(83);
        assertThat(result.reason()).isEqualTo(ChartAssessmentReason.SIGNAL_CONFIRMED);
    }

    @Test
    void confirmsFinalBearishBreakdownWithNeutralLocationAndAlignedMomentum() {
        ChartAssessment result = engine.assess(snapshot(ChartBias.BEARISH, ChartBias.NEUTRAL,
                ChartBias.BEARISH, ChartTrigger.DOWNSIDE_BREAKDOWN, true, true, true,
                ChartReadiness.READY, true));

        assertThat(result.action()).isEqualTo(ChartAction.SELL);
    }

    @Test
    void abstainsOnDirectionalConflictOrMissingParticipation() {
        ChartAssessment conflict = engine.assess(snapshot(ChartBias.BEARISH, ChartBias.BULLISH,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, true,
                ChartReadiness.READY, true));
        ChartAssessment weakParticipation = engine.assess(snapshot(ChartBias.BULLISH, ChartBias.BULLISH,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, false,
                ChartReadiness.READY, true));

        assertThat(conflict.action()).isEqualTo(ChartAction.WAIT);
        assertThat(conflict.reason()).isEqualTo(ChartAssessmentReason.DIRECTION_CONFLICT);
        assertThat(weakParticipation.reason()).isEqualTo(ChartAssessmentReason.CHART_QUALITY_GATE_FAILED);
    }

    @Test
    void provisionalOrStaleEvidenceCannotCreateDirection() {
        ChartAssessment provisional = engine.assess(snapshot(ChartBias.BULLISH, ChartBias.BULLISH,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, true,
                ChartReadiness.READY, false));
        ChartAssessment stale = engine.assess(snapshot(ChartBias.BULLISH, ChartBias.BULLISH,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, true,
                ChartReadiness.STALE, true));

        assertThat(provisional.reason()).isEqualTo(ChartAssessmentReason.EVIDENCE_NOT_READY);
        assertThat(stale.reason()).isEqualTo(ChartAssessmentReason.EVIDENCE_NOT_READY);
    }

    private static ChartSnapshot snapshot(ChartBias higher, ChartBias location, ChartBias momentum,
            ChartTrigger trigger, boolean trend, boolean regime, boolean participation,
            ChartReadiness readiness, boolean finalEvidence) {
        return new ChartSnapshot("chart-1", "NSE", "INE002A01018", higher, location, momentum,
                trigger, trend, regime, participation, 83, readiness, finalEvidence,
                NOW.minusSeconds(30), NOW.minusSeconds(20), NOW.minusSeconds(10), NOW.plusSeconds(50),
                "a".repeat(64), List.of("ema-20-50", "donchian-20", "rsi-14", "relative-volume"));
    }
}
