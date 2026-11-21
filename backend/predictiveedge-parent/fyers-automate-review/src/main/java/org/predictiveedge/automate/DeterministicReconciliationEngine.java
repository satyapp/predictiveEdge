package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Rule-based reconciliation, used when no AI provider is available and as the guardrail for AI output.
 *
 * <ul>
 *   <li>Same direction: keep the direction and take the more conservative level from each side (narrower
 *       entry, tighter stop, nearer targets, smaller quantity).</li>
 *   <li>Opposite directions, or only one side directional: WAIT, because the two engines disagree.</li>
 *   <li>Neither directional: NO_TRADE if either says so, otherwise WAIT.</li>
 * </ul>
 */
public final class DeterministicReconciliationEngine implements ReconciliationEngine {
    @Override public String name() { return "predictiveedge-rules-v1"; }

    @Override
    public Result reconcile(Input input) {
        RecommendationDraft pe = input.stageOne().draft();
        RecommendationDraft fia = input.fiaReview().draft();
        return new Result(merge(pe, fia), name());
    }

    static RecommendationDraft merge(RecommendationDraft pe, RecommendationDraft fia) {
        if (pe.action().isDirectional() && pe.action() == fia.action()) {
            boolean buy = pe.action() == TradeAction.BUY;
            BigDecimal low = max(pe.entryLow(), fia.entryLow());
            BigDecimal high = min(pe.entryHigh(), fia.entryHigh());
            if (low.compareTo(high) > 0) { low = fia.entryLow(); high = fia.entryHigh(); }
            BigDecimal stop = buy ? max(pe.stopLoss(), fia.stopLoss()) : min(pe.stopLoss(), fia.stopLoss());
            BigDecimal t1 = buy ? min(pe.targetOne(), fia.targetOne()) : max(pe.targetOne(), fia.targetOne());
            BigDecimal t2 = buy ? min(pe.targetTwo(), fia.targetTwo()) : max(pe.targetTwo(), fia.targetTwo());
            Integer quantity = Stream.of(pe.quantity(), fia.quantity()).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            Integer confidence = average(pe.confidence(), fia.confidence());
            String rationale = "PredictiveEdge and FIA agree on " + pe.action()
                    + ". The final plan takes the more conservative level from each: narrower entry, tighter stop, "
                    + "nearer targets and the smaller quantity.";
            try {
                return new RecommendationDraft(pe.action(), low, high, stop, t1, t2, quantity, confidence, rationale);
            } catch (IllegalArgumentException conflict) {
                return new RecommendationDraft(fia.action(), fia.entryLow(), fia.entryHigh(), fia.stopLoss(),
                        fia.targetOne(), fia.targetTwo(), quantity, confidence,
                        "PredictiveEdge and FIA agree on " + pe.action() + ", but their levels could not be combined ("
                                + conflict.getMessage() + "), so FIA's trader-reviewed levels are used.");
            }
        }
        if (pe.action().isDirectional() || fia.action().isDirectional()) {
            return new RecommendationDraft(TradeAction.WAIT, null, null, null, null, null, null, 0,
                    "PredictiveEdge says " + pe.action() + " and FIA says " + fia.action()
                            + ". The engines disagree, so the final recommendation is to wait for confirmation.");
        }
        TradeAction action = pe.action() == TradeAction.NO_TRADE || fia.action() == TradeAction.NO_TRADE
                ? TradeAction.NO_TRADE : TradeAction.WAIT;
        return new RecommendationDraft(action, null, null, null, null, null, null, 0,
                "Neither PredictiveEdge nor FIA proposes a directional trade.");
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.max(b);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

    private static Integer average(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.round((a + b) / 2f);
    }
}
