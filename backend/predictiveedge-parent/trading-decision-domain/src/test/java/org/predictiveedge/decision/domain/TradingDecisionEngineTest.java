package org.predictiveedge.decision.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradingDecisionEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-09T05:00:00Z");
    private static final InstrumentRef RELIANCE = new InstrumentRef("NSE", "INE002A01018");
    private final TradingDecisionEngine engine = new TradingDecisionEngine();

    @Test
    void approvesBuyOnlyWhenRequiredDirectionsAgreeAndHardGatesPass() {
        TradingRecommendation result = engine.evaluate("rec-1", intent(RecommendationAction.BUY),
                completeFeedback(RecommendationAction.BUY), NOW);

        assertThat(result.action()).isEqualTo(RecommendationAction.BUY);
        assertThat(result.primaryReason()).isEqualTo(DecisionReason.RECOMMENDATION_APPROVED);
        assertThat(result.confidence()).isEqualTo(71);
        assertThat(result.feedbackReferences()).hasSize(6);
        assertThat(result.evidenceManifestHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void riskVetoCannotBeOverriddenByDirectionalConfidence() {
        List<IntelligenceFeedback> feedback = replace(completeFeedback(RecommendationAction.BUY),
                assessment(IntelligenceModule.RISK, RecommendationAction.WAIT, 99,
                        AssessmentReadiness.READY, GateDisposition.VETO));

        TradingRecommendation result = engine.evaluate("rec-2", intent(RecommendationAction.BUY), feedback, NOW);

        assertThat(result.action()).isEqualTo(RecommendationAction.NO_TRADE);
        assertThat(result.primaryReason()).isEqualTo(DecisionReason.INTELLIGENCE_VETO);
        assertThat(result.blockingModules()).containsExactly(IntelligenceModule.RISK);
        assertThat(result.confidence()).isZero();
    }

    @Test
    void missingOrStaleFeedbackProducesInsufficientEvidence() {
        List<IntelligenceFeedback> missingPortfolio = completeFeedback(RecommendationAction.BUY).stream()
                .filter(value -> value.module() != IntelligenceModule.PORTFOLIO).toList();
        TradingRecommendation missing = engine.evaluate("rec-3", intent(RecommendationAction.BUY),
                missingPortfolio, NOW);

        List<IntelligenceFeedback> staleChart = replace(completeFeedback(RecommendationAction.BUY),
                assessment(IntelligenceModule.CHART, RecommendationAction.BUY, 90,
                        AssessmentReadiness.STALE, GateDisposition.PASS));
        TradingRecommendation stale = engine.evaluate("rec-4", intent(RecommendationAction.BUY), staleChart, NOW);

        assertThat(missing.action()).isEqualTo(RecommendationAction.INSUFFICIENT_EVIDENCE);
        assertThat(missing.blockingModules()).containsExactly(IntelligenceModule.PORTFOLIO);
        assertThat(stale.action()).isEqualTo(RecommendationAction.INSUFFICIENT_EVIDENCE);
        assertThat(stale.blockingModules()).containsExactly(IntelligenceModule.CHART);
    }

    @Test
    void riskAndPortfolioMustExplicitlyPass() {
        List<IntelligenceFeedback> feedback = replace(completeFeedback(RecommendationAction.BUY),
                assessment(IntelligenceModule.PORTFOLIO, RecommendationAction.WAIT, 100,
                        AssessmentReadiness.READY, GateDisposition.NOT_APPLICABLE));

        TradingRecommendation result = engine.evaluate("rec-5", intent(RecommendationAction.BUY), feedback, NOW);

        assertThat(result.action()).isEqualTo(RecommendationAction.INSUFFICIENT_EVIDENCE);
        assertThat(result.blockingModules()).containsExactly(IntelligenceModule.PORTFOLIO);
    }

    @Test
    void directionalConflictProducesWaitInsteadOfForcedTrade() {
        List<IntelligenceFeedback> feedback = replace(completeFeedback(RecommendationAction.BUY),
                assessment(IntelligenceModule.CHART, RecommendationAction.SELL, 95,
                        AssessmentReadiness.READY, GateDisposition.PASS));

        TradingRecommendation result = engine.evaluate("rec-6", intent(RecommendationAction.BUY), feedback, NOW);

        assertThat(result.action()).isEqualTo(RecommendationAction.WAIT);
        assertThat(result.primaryReason()).isEqualTo(DecisionReason.DIRECTION_CONFLICT);
        assertThat(result.blockingModules()).containsExactly(IntelligenceModule.CHART);
    }

    @Test
    void traderIntentLimitsThePermittedRecommendationDirection() {
        TradingRecommendation result = engine.evaluate("rec-7", intent(RecommendationAction.SELL),
                completeFeedback(RecommendationAction.BUY), NOW);

        assertThat(result.action()).isEqualTo(RecommendationAction.NO_TRADE);
        assertThat(result.primaryReason()).isEqualTo(DecisionReason.DIRECTION_NOT_PERMITTED);
    }

    @Test
    void evidenceManifestIsIndependentOfCollectionOrder() {
        List<IntelligenceFeedback> first = completeFeedback(RecommendationAction.BUY);
        List<IntelligenceFeedback> reversed = new ArrayList<>(first);
        java.util.Collections.reverse(reversed);

        TradingRecommendation one = engine.evaluate("rec-8", intent(RecommendationAction.BUY), first, NOW);
        TradingRecommendation two = engine.evaluate("rec-9", intent(RecommendationAction.BUY), reversed, NOW);

        assertThat(two.evidenceManifestHash()).isEqualTo(one.evidenceManifestHash());
        assertThat(two.feedbackReferences()).isEqualTo(one.feedbackReferences());
    }

    @Test
    void rejectsDuplicateModuleFeedbackAndWrongInstrument() {
        List<IntelligenceFeedback> duplicate = new ArrayList<>(completeFeedback(RecommendationAction.BUY));
        duplicate.add(duplicate.getFirst());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> engine.evaluate("rec-10", intent(RecommendationAction.BUY), duplicate, NOW))
                .withMessageContaining("Duplicate feedback");

        List<IntelligenceFeedback> wrongInstrument = new ArrayList<>(completeFeedback(RecommendationAction.BUY));
        IntelligenceFeedback chart = wrongInstrument.getFirst();
        wrongInstrument.set(0, new IntelligenceFeedback(chart.feedbackId(), chart.module(),
                new InstrumentRef("NSE", "INE009A01021"), chart.proposedAction(), chart.confidence(),
                chart.readiness(), chart.gateDisposition(), chart.finalEvidence(), chart.analysisCutoff(),
                chart.knowledgeCutoff(), chart.availableAt(), chart.validUntil(), chart.inputManifestHash(),
                chart.reasons(), chart.evidenceReferences()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> engine.evaluate("rec-11", intent(RecommendationAction.BUY), wrongInstrument, NOW))
                .withMessageContaining("trader-intent instrument");
    }

    private static TraderIntent intent(RecommendationAction permitted) {
        return new TraderIntent("intent-1", UUID.fromString("00000000-0000-0000-0000-000000000001"), RELIANCE,
                EnumSet.of(permitted), "intraday-breakout-v1", NOW.minusSeconds(60), NOW.plusSeconds(300));
    }

    private static List<IntelligenceFeedback> completeFeedback(RecommendationAction direction) {
        return List.of(
                assessment(IntelligenceModule.CHART, direction, 87, AssessmentReadiness.READY, GateDisposition.PASS),
                assessment(IntelligenceModule.SCANNER, direction, 71, AssessmentReadiness.READY, GateDisposition.PASS),
                assessment(IntelligenceModule.STRATEGY, direction, 82, AssessmentReadiness.READY, GateDisposition.PASS),
                assessment(IntelligenceModule.DECISION, direction, 79, AssessmentReadiness.READY, GateDisposition.PASS),
                assessment(IntelligenceModule.RISK, RecommendationAction.WAIT, 100, AssessmentReadiness.READY, GateDisposition.PASS),
                assessment(IntelligenceModule.PORTFOLIO, RecommendationAction.WAIT, 100, AssessmentReadiness.READY, GateDisposition.PASS));
    }

    private static IntelligenceFeedback assessment(IntelligenceModule module, RecommendationAction action,
            int confidence, AssessmentReadiness readiness, GateDisposition disposition) {
        return new IntelligenceFeedback("feedback-" + module.name().toLowerCase(), module, RELIANCE,
                action, confidence, readiness, disposition, true, NOW.minusSeconds(30), NOW.minusSeconds(20),
                NOW.minusSeconds(10), NOW.plusSeconds(60), Integer.toHexString(module.ordinal()).repeat(64),
                List.of("governed assessment"), List.of("evidence:" + module.name().toLowerCase()));
    }

    private static List<IntelligenceFeedback> replace(
            List<IntelligenceFeedback> source, IntelligenceFeedback replacement) {
        List<IntelligenceFeedback> changed = new ArrayList<>(source);
        changed.removeIf(value -> value.module() == replacement.module());
        changed.add(replacement);
        return changed;
    }
}
