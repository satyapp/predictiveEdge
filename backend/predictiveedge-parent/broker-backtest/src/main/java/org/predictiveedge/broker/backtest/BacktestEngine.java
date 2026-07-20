package org.predictiveedge.broker.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.predictiveedge.broker.domain.BrokerOrder;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.OrderRequest;
import org.predictiveedge.broker.domain.OrderSide;
import org.predictiveedge.broker.domain.OrderType;
import org.predictiveedge.broker.paper.PaperBrokerAdapter;
import org.predictiveedge.broker.paper.PaperQuoteBook;
import org.predictiveedge.broker.spi.BrokerContext;

public final class BacktestEngine {
    private final BigDecimal startingCash;

    public BacktestEngine(BigDecimal startingCash) {
        if (startingCash == null || startingCash.signum() <= 0) {
            throw new IllegalArgumentException("Backtest starting cash must be positive");
        }
        this.startingCash = startingCash;
    }

    public BacktestResult run(BrokerContext context, List<Candle> candles, BacktestStrategy strategy) {
        validateCandles(candles);
        java.util.Objects.requireNonNull(context, "Broker context is required");
        java.util.Objects.requireNonNull(strategy, "Strategy is required");

        MutableClock clock = new MutableClock(candles.getFirst().timestamp());
        PaperQuoteBook quotes = new PaperQuoteBook();
        PaperBrokerAdapter paper = new PaperBrokerAdapter(startingCash, quotes, clock);
        List<BrokerOrder> trades = new ArrayList<>();

        for (int index = 0; index < candles.size(); index++) {
            Candle candle = candles.get(index);
            clock.moveTo(candle.timestamp());
            quotes.update(candle.instrument(), candle.close());
            BacktestDecision decision = strategy.decide(new BacktestStep(index, candle, paper.account(context)));
            if (decision.action() == BacktestDecision.Action.HOLD) {
                continue;
            }
            OrderSide side = decision.action() == BacktestDecision.Action.BUY ? OrderSide.BUY : OrderSide.SELL;
            UUID orderId = UUID.nameUUIDFromBytes(
                    (index + ":" + candle.timestamp()).getBytes(StandardCharsets.UTF_8));
            trades.add(paper.placeOrder(context, new OrderRequest(
                    orderId, candle.instrument(), side, OrderType.MARKET, decision.quantity(), null)));
        }

        Candle last = candles.getLast();
        var account = paper.account(context);
        BigDecimal position = account.positions().getOrDefault(last.instrument(), BigDecimal.ZERO);
        BigDecimal finalEquity = account.availableCash().add(position.multiply(last.close()));
        BigDecimal returnPercent = finalEquity.subtract(startingCash)
                .multiply(new BigDecimal("100"))
                .divide(startingCash, 6, RoundingMode.HALF_UP);
        return new BacktestResult(startingCash, account.availableCash(), finalEquity,
                returnPercent, account.positions(), trades);
    }

    private static void validateCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("At least one candle is required");
        }
        for (int index = 1; index < candles.size(); index++) {
            Candle previous = candles.get(index - 1);
            Candle current = candles.get(index);
            if (!previous.instrument().equals(current.instrument())) {
                throw new IllegalArgumentException("A backtest run currently supports one instrument");
            }
            if (!previous.timestamp().isBefore(current.timestamp())) {
                throw new IllegalArgumentException("Candles must be strictly chronological");
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) { this.current = initial; }
        private void moveTo(Instant instant) { this.current = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
