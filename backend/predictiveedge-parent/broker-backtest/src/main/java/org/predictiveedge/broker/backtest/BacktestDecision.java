package org.predictiveedge.broker.backtest;

import java.math.BigDecimal;
import java.util.Objects;

public record BacktestDecision(Action action, BigDecimal quantity) {
    public BacktestDecision {
        Objects.requireNonNull(action, "Action is required");
        Objects.requireNonNull(quantity, "Quantity is required");
        if (action == Action.HOLD && quantity.signum() != 0) {
            throw new IllegalArgumentException("Hold quantity must be zero");
        }
        if (action != Action.HOLD && quantity.signum() <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
    }

    public static BacktestDecision hold() { return new BacktestDecision(Action.HOLD, BigDecimal.ZERO); }
    public static BacktestDecision buy(BigDecimal quantity) { return new BacktestDecision(Action.BUY, quantity); }
    public static BacktestDecision sell(BigDecimal quantity) { return new BacktestDecision(Action.SELL, quantity); }

    public enum Action { HOLD, BUY, SELL }
}
