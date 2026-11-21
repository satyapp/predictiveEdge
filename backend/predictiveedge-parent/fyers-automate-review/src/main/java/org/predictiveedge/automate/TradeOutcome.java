package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The trade the trader actually took in FYERS, recorded after exit. Realized P/L is derived, never entered. */
public record TradeOutcome(
        TradeAction direction,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        Instant enteredAt,
        Instant exitedAt,
        BigDecimal charges,
        RecommendationStage followedStage,
        UUID monitoringCaseId,
        String notes,
        Instant recordedAt) {

    public TradeOutcome {
        if (direction == null || !direction.isDirectional()) {
            throw new IllegalArgumentException("Outcome direction must be BUY or SELL");
        }
        positive(quantity, "Quantity");
        positive(entryPrice, "Entry price");
        positive(exitPrice, "Exit price");
        Objects.requireNonNull(enteredAt, "Entry time is required");
        Objects.requireNonNull(exitedAt, "Exit time is required");
        if (exitedAt.isBefore(enteredAt)) throw new IllegalArgumentException("Exit cannot precede entry");
        charges = charges == null ? BigDecimal.ZERO : charges;
        if (charges.signum() < 0) throw new IllegalArgumentException("Charges cannot be negative");
        Objects.requireNonNull(followedStage, "Followed stage is required");
        notes = notes == null ? "" : notes.strip();
        Objects.requireNonNull(recordedAt, "Recorded time is required");
    }

    /** Net realized P/L after charges, in rupees. */
    public BigDecimal realizedPnl() {
        BigDecimal perShare = direction == TradeAction.BUY ? exitPrice.subtract(entryPrice) : entryPrice.subtract(exitPrice);
        return perShare.multiply(quantity).subtract(charges).setScale(2, RoundingMode.HALF_UP);
    }

    /** Return on the capital deployed at entry, in percent. */
    public BigDecimal returnPercent() {
        BigDecimal deployed = entryPrice.multiply(quantity);
        return realizedPnl().multiply(BigDecimal.valueOf(100)).divide(deployed, 2, RoundingMode.HALF_UP);
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
