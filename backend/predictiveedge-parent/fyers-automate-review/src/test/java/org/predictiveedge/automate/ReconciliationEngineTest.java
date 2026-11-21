package org.predictiveedge.automate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void oppositeDirectionsReconcileToWait() {
        var result = new DeterministicReconciliationEngine().reconcile(input(
                AutomateReviewServiceTest.buy("100", "98", "104", null, 60, "PE long"),
                new RecommendationDraft(TradeAction.SELL, bd("100"), bd("100"), bd("102"), bd("96"), null, null, 70, "FIA short")));
        assertThat(result.draft().action()).isEqualTo(TradeAction.WAIT);
        assertThat(result.draft().rationale()).contains("disagree");
    }

    @Test
    void sameDirectionTakesConservativeLevels() {
        var result = new DeterministicReconciliationEngine().reconcile(input(
                new RecommendationDraft(TradeAction.SELL, bd("99"), bd("101"), bd("103"), bd("95"), bd("92"), 30, 60, "PE"),
                new RecommendationDraft(TradeAction.SELL, bd("100"), bd("102"), bd("102.5"), bd("96"), null, 20, 80, "FIA")));
        RecommendationDraft d = result.draft();
        assertThat(d.action()).isEqualTo(TradeAction.SELL);
        assertThat(d.entryLow()).isEqualByComparingTo("100");
        assertThat(d.entryHigh()).isEqualByComparingTo("101");
        assertThat(d.stopLoss()).isEqualByComparingTo("102.5");
        assertThat(d.targetOne()).isEqualByComparingTo("96");
        assertThat(d.targetTwo()).isEqualByComparingTo("92");
        assertThat(d.quantity()).isEqualTo(20);
        assertThat(d.confidence()).isEqualTo(70);
    }

    @Test
    void aiResultIsUsedWhenValid() {
        String output = "{\"action\":\"BUY\",\"entry_low\":100,\"entry_high\":101,\"stop_loss\":98.5,\"target_one\":104,"
                + "\"target_two\":null,\"quantity\":25,\"confidence\":66,\"rationale\":\"Agreed with FIA on the stop.\"}";
        var engine = new AiReconciliationEngine(json, (key, request) -> wrap(output), "ollama", "local", "qwen3:8b");
        var result = engine.reconcile(input(AutomateReviewServiceTest.buy("100", "98", "104", null, 60, "PE"),
                AutomateReviewServiceTest.buy("100", "99", "103", null, 70, "FIA")));
        assertThat(result.engine()).isEqualTo("ollama:qwen3:8b");
        assertThat(result.draft().stopLoss()).isEqualByComparingTo("98.5");
        assertThat(result.draft().quantity()).isEqualTo(25);
    }

    @Test
    void aiDirectionNeitherEngineProposedFallsBackToRules() {
        String output = "{\"action\":\"SELL\",\"entry_low\":100,\"entry_high\":100,\"stop_loss\":102,\"target_one\":96,"
                + "\"target_two\":null,\"quantity\":null,\"confidence\":50,\"rationale\":\"x\"}";
        var engine = new AiReconciliationEngine(json, (key, request) -> wrap(output), "openai", "k", "m");
        var result = engine.reconcile(input(AutomateReviewServiceTest.buy("100", "98", "104", null, 60, "PE"),
                AutomateReviewServiceTest.buy("100", "99", "103", null, 70, "FIA")));
        assertThat(result.engine()).isEqualTo("predictiveedge-rules-v1");
        assertThat(result.draft().action()).isEqualTo(TradeAction.BUY);
        assertThat(result.draft().rationale()).contains("unavailable");
    }

    @Test
    void aiOutageFallsBackToRules() {
        var engine = new AiReconciliationEngine(json, (key, request) -> { throw new IllegalStateException("down"); },
                "ollama", "local", "m");
        var result = engine.reconcile(input(AutomateReviewServiceTest.buy("100", "98", "104", null, 60, "PE"),
                AutomateReviewServiceTest.buy("100", "99", "103", null, 70, "FIA")));
        assertThat(result.draft().stopLoss()).isEqualByComparingTo("99");
        assertThat(result.draft().rationale()).contains("down");
    }

    @Test
    void draftRejectsStopsOnTheWrongSide() {
        assertThatThrownBy(() -> AutomateReviewServiceTest.buy("100", "101", "104", null, null, "x"))
                .hasMessageContaining("below");
        assertThatThrownBy(() -> new RecommendationDraft(TradeAction.SELL, bd("100"), bd("100"), bd("99"), null, null, null, null, ""))
                .hasMessageContaining("above");
    }

    @Test
    void outcomePnlForShortTrade() {
        var o = new TradeOutcome(TradeAction.SELL, bd("10"), bd("200"), bd("190"), Instant.parse("2026-11-23T04:00:00Z"),
                Instant.parse("2026-11-23T05:00:00Z"), bd("5"), RecommendationStage.FINAL_AI_FIA, null, null, Instant.now());
        assertThat(o.realizedPnl()).isEqualByComparingTo("95.00");
        assertThat(o.returnPercent()).isEqualByComparingTo("4.75");
    }

    private String wrap(String text) {
        var root = json.createObjectNode();
        root.putArray("output").addObject().put("type", "message").putArray("content").addObject()
                .put("type", "output_text").put("text", text);
        return root.toString();
    }

    private static ReconciliationEngine.Input input(RecommendationDraft pe, RecommendationDraft fia) {
        Instant now = Instant.parse("2026-11-23T04:00:00Z");
        return new ReconciliationEngine.Input("NSE", "SBIN",
                new RecommendationVersion(UUID.randomUUID(), RecommendationStage.PREDICTIVEEDGE_STAGE_ONE, pe, "PE", now),
                new RecommendationVersion(UUID.randomUUID(), RecommendationStage.FIA_REVIEW, fia, "FIA", now),
                LearningSummary.from(List.of()), List.of());
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
