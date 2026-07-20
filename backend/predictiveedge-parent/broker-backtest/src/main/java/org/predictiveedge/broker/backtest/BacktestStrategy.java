package org.predictiveedge.broker.backtest;

@FunctionalInterface
public interface BacktestStrategy {
    BacktestDecision decide(BacktestStep step);
}
