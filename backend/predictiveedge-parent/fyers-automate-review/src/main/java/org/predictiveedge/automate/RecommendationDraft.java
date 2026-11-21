package org.predictiveedge.automate;

import java.math.BigDecimal;

/**
 * Trade levels proposed at one stage. Directional drafts need an entry and a stop on the protective side.
 * Prices are optional for WAIT and NO_TRADE.
 */
public record RecommendationDraft(
        TradeAction action,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal stopLoss,
        BigDecimal targetOne,
        BigDecimal targetTwo,
        Integer quantity,
        Integer confidence,
        String rationale) {

    public RecommendationDraft {
        if (action == null) throw new IllegalArgumentException("Action is required");
        rationale = rationale == null ? "" : rationale.strip();
        if (rationale.length() > 8000) throw new IllegalArgumentException("Rationale must be at most 8000 characters");
        positive(entryLow, "Entry low");
        positive(entryHigh, "Entry high");
        positive(stopLoss, "Stop loss");
        positive(targetOne, "Target 1");
        positive(targetTwo, "Target 2");
        if (quantity != null && quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        if (confidence != null && (confidence < 0 || confidence > 100)) {
            throw new IllegalArgumentException("Confidence must be between 0 and 100");
        }
        if (entryLow != null && entryHigh == null) entryHigh = entryLow;
        if (entryHigh != null && entryLow == null) entryLow = entryHigh;
        if (entryLow != null && entryLow.compareTo(entryHigh) > 0) {
            throw new IllegalArgumentException("Entry low cannot be above entry high");
        }
        if (action.isDirectional()) {
            if (entryLow == null) throw new IllegalArgumentException(action + " needs an entry price");
            if (stopLoss == null) throw new IllegalArgumentException(action + " needs a stop loss");
            boolean buy = action == TradeAction.BUY;
            if (buy ? stopLoss.compareTo(entryLow) >= 0 : stopLoss.compareTo(entryHigh) <= 0) {
                throw new IllegalArgumentException(buy ? "A BUY stop loss must be below the entry"
                        : "A SELL stop loss must be above the entry");
            }
            for (BigDecimal target : new BigDecimal[] {targetOne, targetTwo}) {
                if (target != null && (buy ? target.compareTo(entryHigh) <= 0 : target.compareTo(entryLow) >= 0)) {
                    throw new IllegalArgumentException(buy ? "BUY targets must be above the entry"
                            : "SELL targets must be below the entry");
                }
            }
        }
    }

    private static void positive(BigDecimal value, String name) {
        if (value != null && value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
