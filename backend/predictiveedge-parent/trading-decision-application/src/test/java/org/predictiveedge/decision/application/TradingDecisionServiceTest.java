package org.predictiveedge.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.IntelligenceFeedback;
import org.predictiveedge.decision.domain.IntelligenceModule;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.TraderIntent;
import org.predictiveedge.decision.domain.TradingDecisionEngine;

class TradingDecisionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T05:00:00Z");

    @Test
    void usesOnePointInTimeCutoffForFeedbackAndEvaluation() {
        InstrumentRef instrument = new InstrumentRef("NSE", "INE002A01018");
        TraderIntent intent = new TraderIntent("intent-1", UUID.randomUUID(), instrument,
                EnumSet.of(RecommendationAction.BUY), "breakout-v1", NOW.minusSeconds(1), NOW.plusSeconds(60));
        AtomicReference<Instant> queriedAt = new AtomicReference<>();
        IntelligenceFeedbackQuery query = (requestedIntent, cutoff) -> {
            queriedAt.set(cutoff);
            return List.of(
                    feedback(IntelligenceModule.CHART, RecommendationAction.BUY, instrument),
                    feedback(IntelligenceModule.SCANNER, RecommendationAction.BUY, instrument),
                    feedback(IntelligenceModule.STRATEGY, RecommendationAction.BUY, instrument),
                    feedback(IntelligenceModule.DECISION, RecommendationAction.BUY, instrument),
                    feedback(IntelligenceModule.RISK, RecommendationAction.WAIT, instrument),
                    feedback(IntelligenceModule.PORTFOLIO, RecommendationAction.WAIT, instrument));
        };
        TradingDecisionService service = new TradingDecisionService(query, new TradingDecisionEngine(),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "recommendation-1");

        var result = service.recommend(intent);

        assertThat(queriedAt.get()).isEqualTo(NOW);
        assertThat(result.evaluatedAt()).isEqualTo(NOW);
        assertThat(result.action()).isEqualTo(RecommendationAction.BUY);
        assertThat(result.recommendationId()).isEqualTo("recommendation-1");
    }

    private static IntelligenceFeedback feedback(
            IntelligenceModule module, RecommendationAction action, InstrumentRef instrument) {
        return new IntelligenceFeedback("feedback-" + module.name(), module, instrument, action, 80,
                AssessmentReadiness.READY, GateDisposition.PASS, true, NOW.minusSeconds(3),
                NOW.minusSeconds(2), NOW.minusSeconds(1), NOW.plusSeconds(30),
                Integer.toHexString(module.ordinal()).repeat(64), List.of("ready"), List.of("evidence"));
    }
}
