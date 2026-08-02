package org.predictiveedge.broker.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerContext;

class BacktestEngineTest {
    private static final Instrument INFY = new Instrument("NSE", "INFY");
    private static final Instant START = Instant.parse("2026-01-01T03:45:00Z");

    @Test
    void replaysStrategyOrdersThroughPaperTrading() {
        var engine = new BacktestEngine(new BigDecimal("1000"));
        List<Candle> candles = List.of(
                candle(0, "100"), candle(1, "110"), candle(2, "120"), candle(3, "130"));

        BacktestResult result = engine.run(
                BrokerContext.withoutCredentials(UUID.randomUUID(), "backtest"),
                candles,
                step -> {
                    if (step.index() == 0) return BacktestDecision.buy(new BigDecimal("5"));
                    if (step.index() == 2) return BacktestDecision.sell(new BigDecimal("5"));
                    return BacktestDecision.hold();
                });

        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().getFirst().createdAt()).isEqualTo(candles.get(1).timestamp());
        assertThat(result.trades().getFirst().averageFillPrice()).isEqualByComparingTo("110");
        assertThat(result.trades().getLast().createdAt()).isEqualTo(candles.get(3).timestamp());
        assertThat(result.trades().getLast().averageFillPrice()).isEqualByComparingTo("130");
        assertThat(result.finalCash()).isEqualByComparingTo("1100");
        assertThat(result.finalEquity()).isEqualByComparingTo("1100");
        assertThat(result.totalReturnPercent()).isEqualByComparingTo("10.000000");
        assertThat(result.finalPositions()).isEmpty();
    }

    @Test
    void doesNotFillACompletedCandleDecisionOnTheSameCandle() {
        var engine = new BacktestEngine(new BigDecimal("1000"));
        List<Candle> candles = List.of(candle(0, "100"), candle(1, "110"));

        BacktestResult result = engine.run(
                BrokerContext.withoutCredentials(UUID.randomUUID(), "backtest"),
                candles,
                step -> step.index() == 1
                        ? BacktestDecision.buy(BigDecimal.ONE)
                        : BacktestDecision.hold());

        assertThat(result.trades()).isEmpty();
        assertThat(result.finalCash()).isEqualByComparingTo("1000");
        assertThat(result.finalPositions()).isEmpty();
    }

    @Test
    void rejectsNonChronologicalDataToPreventLookAhead() {
        var engine = new BacktestEngine(new BigDecimal("1000"));

        assertThatThrownBy(() -> engine.run(
                BrokerContext.withoutCredentials(UUID.randomUUID(), "backtest"),
                List.of(candle(1, "110"), candle(0, "100")),
                step -> BacktestDecision.hold()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chronological");
    }

    private static Candle candle(int minutes, String close) {
        BigDecimal price = new BigDecimal(close);
        return new Candle(INFY, START.plus(minutes, ChronoUnit.MINUTES), price, price, price, price, 1000, null);
    }
}
