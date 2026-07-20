package org.predictiveedge.broker.backtest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.predictiveedge.broker.domain.BrokerOrder;
import org.predictiveedge.broker.domain.Instrument;

public record BacktestResult(
        BigDecimal initialCash,
        BigDecimal finalCash,
        BigDecimal finalEquity,
        BigDecimal totalReturnPercent,
        Map<Instrument, BigDecimal> finalPositions,
        List<BrokerOrder> trades) {

    public BacktestResult {
        finalPositions = Map.copyOf(finalPositions);
        trades = List.copyOf(trades);
    }
}
