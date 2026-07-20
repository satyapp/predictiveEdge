package org.predictiveedge.broker.backtest;

import org.predictiveedge.broker.domain.BrokerAccount;
import org.predictiveedge.broker.domain.Candle;

public record BacktestStep(int index, Candle candle, BrokerAccount account) {}
