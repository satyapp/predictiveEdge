package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Point-in-time execution feasibility; it never places or represents an order. */
public record ExecutionContext(
        Instant observedAt,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        String depthSnapshotRef,
        RecommendationAction direction,
        int proposedQuantity,
        long visibleEntryQuantity,
        long visibleExitQuantity,
        BigDecimal estimatedFillPrice,
        BigDecimal spreadBps,
        BigDecimal estimatedSlippageBps,
        BigDecimal estimatedFeesAndTaxes,
        BigDecimal estimatedMarketImpactBps,
        long decisionToOrderLatencyMillis,
        boolean entryFeasible,
        boolean exitFeasible,
        Instant validUntil) {

    public ExecutionContext {
        Objects.requireNonNull(observedAt, "Observed-at time is required");
        positive(bestBid, "Best bid");
        positive(bestAsk, "Best ask");
        if (bestAsk.compareTo(bestBid) < 0) throw new IllegalArgumentException("Best ask cannot be below best bid");
        if (depthSnapshotRef == null || depthSnapshotRef.isBlank()) {
            throw new IllegalArgumentException("Depth snapshot reference is required");
        }
        depthSnapshotRef = depthSnapshotRef.trim();
        Objects.requireNonNull(direction, "Execution direction is required");
        if (!direction.isDirectional()) throw new IllegalArgumentException("Execution direction must be BUY or SELL");
        if (proposedQuantity <= 0) throw new IllegalArgumentException("Proposed quantity must be positive");
        if (visibleEntryQuantity < 0 || visibleExitQuantity < 0)
            throw new IllegalArgumentException("Visible depth quantities cannot be negative");
        positive(estimatedFillPrice, "Estimated fill price");
        nonNegative(spreadBps, "Spread bps");
        nonNegative(estimatedSlippageBps, "Estimated slippage bps");
        nonNegative(estimatedFeesAndTaxes, "Estimated fees and taxes");
        nonNegative(estimatedMarketImpactBps, "Estimated market impact bps");
        if (decisionToOrderLatencyMillis < 0) throw new IllegalArgumentException("Latency cannot be negative");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (!observedAt.isBefore(validUntil)) throw new IllegalArgumentException("Execution context validity is invalid");
    }

    /** Compatibility constructor for previously stored/tested contexts; new producers use explicit fields. */
    public ExecutionContext(Instant observedAt, BigDecimal bestBid, BigDecimal bestAsk,
            String depthSnapshotRef, int proposedQuantity, BigDecimal estimatedFillPrice,
            BigDecimal spreadBps, BigDecimal estimatedSlippageBps, BigDecimal estimatedFeesAndTaxes,
            BigDecimal estimatedMarketImpactBps, long decisionToOrderLatencyMillis,
            boolean entryFeasible, boolean exitFeasible, Instant validUntil) {
        this(observedAt, bestBid, bestAsk, depthSnapshotRef, RecommendationAction.BUY, proposedQuantity,
                proposedQuantity, proposedQuantity, estimatedFillPrice, spreadBps, estimatedSlippageBps,
                estimatedFeesAndTaxes, estimatedMarketImpactBps, decisionToOrderLatencyMillis,
                entryFeasible, exitFeasible, validUntil);
    }

    public boolean isUsableAt(Instant cutoff) {
        return !observedAt.isAfter(cutoff) && cutoff.isBefore(validUntil) && entryFeasible && exitFeasible;
    }

    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " cannot be negative");
    }
}
