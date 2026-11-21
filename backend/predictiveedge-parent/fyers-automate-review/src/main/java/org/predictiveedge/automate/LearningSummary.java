package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What past closed trades say about each stage's recommendations. It is fed into reconciliation so later
 * decisions for the same stock, and for others, can weigh PredictiveEdge and FIA by their realized record.
 */
public record LearningSummary(int closedTrades, int winningTrades, BigDecimal totalPnl,
        Map<RecommendationStage, StageRecord> byFollowedStage, int fiaAgreedWithStageOne, int fiaDisagreedWithStageOne,
        int stageOneDirectionCorrect, int fiaDirectionCorrect) {

    public record StageRecord(int trades, int wins, BigDecimal pnl) { }

    public static LearningSummary from(List<RecommendationReview> closed) {
        Map<RecommendationStage, StageRecord> stages = new EnumMap<>(RecommendationStage.class);
        int wins = 0, agreed = 0, disagreed = 0, stageOneCorrect = 0, fiaCorrect = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (RecommendationReview review : closed) {
            TradeOutcome outcome = review.outcome();
            if (outcome == null) continue;
            BigDecimal pnl = outcome.realizedPnl();
            boolean win = pnl.signum() > 0;
            if (win) wins++;
            total = total.add(pnl);
            stages.merge(outcome.followedStage(), new StageRecord(1, win ? 1 : 0, pnl),
                    (a, b) -> new StageRecord(a.trades() + b.trades(), a.wins() + b.wins(), a.pnl().add(b.pnl())));
            // Which direction the market rewarded: the traded side if it made money, else the opposite side.
            TradeAction rewarded = win ? outcome.direction()
                    : outcome.direction() == TradeAction.BUY ? TradeAction.SELL : TradeAction.BUY;
            var stageOne = review.latest(RecommendationStage.PREDICTIVEEDGE_STAGE_ONE).map(v -> v.draft().action());
            var fia = review.latest(RecommendationStage.FIA_REVIEW).map(v -> v.draft().action());
            if (stageOne.isPresent() && fia.isPresent()) {
                if (stageOne.get() == fia.get()) agreed++; else disagreed++;
            }
            if (stageOne.filter(a -> a == rewarded).isPresent()) stageOneCorrect++;
            if (fia.filter(a -> a == rewarded).isPresent()) fiaCorrect++;
        }
        int trades = (int) closed.stream().filter(r -> r.outcome() != null).count();
        return new LearningSummary(trades, wins, total.setScale(2, RoundingMode.HALF_UP), stages, agreed, disagreed,
                stageOneCorrect, fiaCorrect);
    }

    public BigDecimal winRatePercent() {
        return closedTrades == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(winningTrades * 100L).divide(BigDecimal.valueOf(closedTrades), 1, RoundingMode.HALF_UP);
    }
}
