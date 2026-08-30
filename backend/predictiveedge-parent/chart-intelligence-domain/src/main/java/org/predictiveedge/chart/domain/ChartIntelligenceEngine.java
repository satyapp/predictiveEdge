package org.predictiveedge.chart.domain;

import java.util.Objects;

/** Conservative intraday chart interpretation aligned with the governed indicator profile. */
public final class ChartIntelligenceEngine {
    public ChartAssessment assess(ChartSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Chart snapshot is required");
        if (snapshot.readiness() != ChartReadiness.READY || !snapshot.finalEvidence()) {
            return waitFor(ChartAssessmentReason.EVIDENCE_NOT_READY);
        }
        if (snapshot.trigger() == ChartTrigger.NONE) {
            return waitFor(ChartAssessmentReason.NO_FINAL_TRIGGER);
        }
        if (!snapshot.trendQualified() || !snapshot.regimePermitsSetup()
                || !snapshot.participationConfirmed()) {
            return waitFor(ChartAssessmentReason.CHART_QUALITY_GATE_FAILED);
        }

        ChartBias candidate = snapshot.trigger() == ChartTrigger.UPSIDE_BREAKOUT
                ? ChartBias.BULLISH : ChartBias.BEARISH;
        if (snapshot.higherTimeframeBias() != candidate
                || contradicts(snapshot.sessionLocationBias(), candidate)
                || contradicts(snapshot.momentumBias(), candidate)) {
            return waitFor(ChartAssessmentReason.DIRECTION_CONFLICT);
        }
        ChartAction action = candidate == ChartBias.BULLISH ? ChartAction.BUY : ChartAction.SELL;
        return new ChartAssessment(action, snapshot.confidence(), ChartAssessmentReason.SIGNAL_CONFIRMED);
    }

    private static boolean contradicts(ChartBias evidence, ChartBias candidate) {
        return evidence != ChartBias.NEUTRAL && evidence != candidate;
    }

    private static ChartAssessment waitFor(ChartAssessmentReason reason) {
        return new ChartAssessment(ChartAction.WAIT, 0, reason);
    }
}
