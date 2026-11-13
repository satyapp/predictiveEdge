package org.predictiveedge.chart.domain;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic pre-open screen. It emits candidates, never executable orders or final recommendations. */
public final class PreOpenIntradayAnalyzer {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);
    private final Policy policy;

    public PreOpenIntradayAnalyzer(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "Analysis policy is required");
    }

    public IntradaySetupAssessment analyze(
            PreOpenSnapshot snapshot, List<IntradayHistoricalBar> suppliedBars, Instant analysisCutoff) {
        Objects.requireNonNull(snapshot, "Pre-open snapshot is required");
        Objects.requireNonNull(suppliedBars, "Historical bars are required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        if (snapshot.observedAt().isAfter(analysisCutoff))
            throw new IllegalArgumentException("Pre-open snapshot is not available at the analysis cutoff");

        BigDecimal gap = percent(snapshot.auctionPrice().subtract(snapshot.previousClose()), snapshot.previousClose());
        BigDecimal spread = snapshot.bestAsk().subtract(snapshot.bestBid()).multiply(TEN_THOUSAND, MC)
                .divide(snapshot.bestAsk().add(snapshot.bestBid()).divide(BigDecimal.valueOf(2), MC), MC);

        if (!"EQ".equals(snapshot.series())) {
            return empty(snapshot, IntradaySetupAction.NO_TRADE, gap, spread, analysisCutoff,
                    "Only EQ-series instruments are eligible for intraday screening");
        }
        if (spread.compareTo(policy.maximumSpreadBps()) > 0) {
            return empty(snapshot, IntradaySetupAction.NO_TRADE, gap, spread, analysisCutoff,
                    "Pre-open spread exceeds the configured liquidity limit");
        }
        if (snapshot.auctionVolume() == 0 || snapshot.auctionTurnover().compareTo(policy.minimumAuctionTurnover()) < 0) {
            return empty(snapshot, IntradaySetupAction.NO_TRADE, gap, spread, analysisCutoff,
                    "Pre-open auction participation is below the configured liquidity floor");
        }

        List<IntradayHistoricalBar> bars = suppliedBars.stream()
                .filter(bar -> !bar.endsAt().isAfter(analysisCutoff) && !bar.availableAt().isAfter(analysisCutoff))
                .sorted(Comparator.comparing(IntradayHistoricalBar::startsAt)).toList();
        if (bars.size() < policy.minimumBars()) {
            return empty(snapshot, IntradaySetupAction.INSUFFICIENT_EVIDENCE, gap, spread, analysisCutoff,
                    "At least " + policy.minimumBars() + " finalized five-minute bars are required");
        }

        List<BigDecimal> closes = bars.stream().map(IntradayHistoricalBar::close).toList();
        BigDecimal ema20 = ema(closes, 20);
        BigDecimal ema50 = ema(closes, 50);
        BigDecimal rsi14 = rsi(closes, 14);
        BigDecimal atr14 = atr(bars, 14);
        BigDecimal auctionVolumeRatio = volumeRatio(snapshot.auctionVolume(), bars);
        BigDecimal lastClose = closes.getLast();
        Trend trend = lastClose.compareTo(ema20) > 0 && ema20.compareTo(ema50) > 0 ? Trend.BULLISH
                : lastClose.compareTo(ema20) < 0 && ema20.compareTo(ema50) < 0 ? Trend.BEARISH : Trend.NEUTRAL;

        var evidence = bars.subList(Math.max(0, bars.size() - 50), bars.size()).stream()
                .map(IntradayHistoricalBar::evidenceReference).distinct().toList();
        IntradaySetupAction action = IntradaySetupAction.WAIT;
        IntradaySetupType type = IntradaySetupType.NONE;
        String confirmation = "WAIT_FOR_FIRST_FINAL_5_MINUTE_BAR";
        BigDecimal entry = null;
        var reasons = new ArrayList<String>();

        boolean normalGap = gap.abs().compareTo(policy.minimumAbsoluteGapPercent()) >= 0
                && gap.abs().compareTo(policy.maximumAbsoluteGapPercent()) <= 0;
        if (!normalGap) {
            reasons.add("Gap is outside the configured continuation range");
        } else if (gap.signum() > 0 && trend == Trend.BULLISH && between(rsi14, policy.longMinimumRsi(), policy.longMaximumRsi())) {
            action = IntradaySetupAction.BUY_CANDIDATE;
            type = IntradaySetupType.GAP_CONTINUATION;
            confirmation = "BUY_ONLY_ABOVE_FIRST_5_MINUTE_HIGH_AND_SESSION_VWAP";
            entry = snapshot.auctionPrice();
            reasons.add("Positive auction gap aligns with bullish EMA20/EMA50 structure");
            reasons.add("RSI14 is inside the governed long-momentum range");
        } else if (gap.signum() < 0 && trend == Trend.BEARISH && between(rsi14, policy.shortMinimumRsi(), policy.shortMaximumRsi())) {
            action = IntradaySetupAction.SELL_CANDIDATE;
            type = IntradaySetupType.GAP_CONTINUATION;
            confirmation = "SELL_ONLY_BELOW_FIRST_5_MINUTE_LOW_AND_SESSION_VWAP";
            entry = snapshot.auctionPrice();
            reasons.add("Negative auction gap aligns with bearish EMA20/EMA50 structure");
            reasons.add("RSI14 is inside the governed short-momentum range");
        } else if (gap.signum() > 0 && trend != Trend.BULLISH) {
            action = IntradaySetupAction.SELL_CANDIDATE;
            type = IntradaySetupType.FAILED_GAP_REVERSAL;
            confirmation = "SELL_ONLY_AFTER_FIRST_5_MINUTE_CLOSE_BELOW_PREVIOUS_CLOSE_AND_SESSION_VWAP";
            entry = snapshot.previousClose();
            reasons.add("Positive auction gap conflicts with the established trend");
        } else if (gap.signum() < 0 && trend != Trend.BEARISH) {
            action = IntradaySetupAction.BUY_CANDIDATE;
            type = IntradaySetupType.FAILED_GAP_REVERSAL;
            confirmation = "BUY_ONLY_AFTER_FIRST_5_MINUTE_CLOSE_ABOVE_PREVIOUS_CLOSE_AND_SESSION_VWAP";
            entry = snapshot.previousClose();
            reasons.add("Negative auction gap conflicts with the established trend");
        } else {
            reasons.add("Trend, gap and momentum do not form a qualified setup");
        }

        BigDecimal stop = null, targetOne = null, targetTwo = null;
        if (entry != null) {
            BigDecimal riskDistance = atr14.multiply(policy.atrStopMultiple(), MC)
                    .max(entry.multiply(policy.minimumStopPercent(), MC).divide(HUNDRED, MC));
            if (action == IntradaySetupAction.BUY_CANDIDATE) {
                stop = price(entry.subtract(riskDistance));
                targetOne = price(entry.add(riskDistance));
                targetTwo = price(entry.add(riskDistance.multiply(BigDecimal.valueOf(2), MC)));
            } else {
                stop = price(entry.add(riskDistance));
                targetOne = price(entry.subtract(riskDistance));
                targetTwo = price(entry.subtract(riskDistance.multiply(BigDecimal.valueOf(2), MC)));
            }
            reasons.add("Levels are indicative until the required opening-range and VWAP confirmation completes");
        }
        reasons.add("Auction volume is " + auctionVolumeRatio.setScale(2, HALF_UP)
                + "x the median volume of the latest fifty finalized five-minute bars");
        return new IntradaySetupAssessment(snapshot.venue(), snapshot.symbol(), action, type, confirmation,
                value(gap), price(ema20), price(ema50), value(rsi14), price(atr14), value(spread),
                snapshot.auctionTurnover().setScale(2, HALF_UP), value(auctionVolumeRatio), price(entry),
                stop, targetOne, targetTwo, analysisCutoff, reasons, evidence);
    }

    private static IntradaySetupAssessment empty(PreOpenSnapshot snapshot, IntradaySetupAction action,
            BigDecimal gap, BigDecimal spread, Instant cutoff, String reason) {
        return new IntradaySetupAssessment(snapshot.venue(), snapshot.symbol(), action, IntradaySetupType.NONE,
                "NO_ENTRY", value(gap), null, null, null, null, value(spread),
                snapshot.auctionTurnover().setScale(2, HALF_UP), null, null, null, null, null,
                cutoff, List.of(reason), List.of());
    }

    private static BigDecimal ema(List<BigDecimal> values, int period) {
        if (values.size() < period) throw new IllegalArgumentException("EMA warm-up is incomplete");
        BigDecimal result = values.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), MC);
        BigDecimal multiplier = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), MC);
        for (int index = period; index < values.size(); index++)
            result = values.get(index).subtract(result).multiply(multiplier, MC).add(result, MC);
        return result;
    }

    private static BigDecimal rsi(List<BigDecimal> closes, int period) {
        BigDecimal gains = BigDecimal.ZERO, losses = BigDecimal.ZERO;
        for (int index = 1; index <= period; index++) {
            BigDecimal change = closes.get(index).subtract(closes.get(index - 1));
            gains = gains.add(change.max(BigDecimal.ZERO));
            losses = losses.add(change.negate().max(BigDecimal.ZERO));
        }
        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(period), MC);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(period), MC);
        for (int index = period + 1; index < closes.size(); index++) {
            BigDecimal change = closes.get(index).subtract(closes.get(index - 1));
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1L), MC).add(change.max(BigDecimal.ZERO), MC)
                    .divide(BigDecimal.valueOf(period), MC);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1L), MC).add(change.negate().max(BigDecimal.ZERO), MC)
                    .divide(BigDecimal.valueOf(period), MC);
        }
        if (avgLoss.signum() == 0) return avgGain.signum() == 0 ? BigDecimal.valueOf(50) : HUNDRED;
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MC));
    }

    private static BigDecimal atr(List<IntradayHistoricalBar> bars, int period) {
        int start = bars.size() - period;
        BigDecimal total = BigDecimal.ZERO;
        for (int index = start; index < bars.size(); index++) {
            var current = bars.get(index);
            BigDecimal priorClose = bars.get(index - 1).close();
            BigDecimal range = current.high().subtract(current.low())
                    .max(current.high().subtract(priorClose).abs())
                    .max(current.low().subtract(priorClose).abs());
            total = total.add(range);
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }

    private static BigDecimal volumeRatio(long auctionVolume, List<IntradayHistoricalBar> bars) {
        List<Long> volumes = bars.subList(Math.max(0, bars.size() - 50), bars.size()).stream()
                .map(IntradayHistoricalBar::volume).filter(value -> value > 0).sorted().toList();
        if (volumes.isEmpty()) return BigDecimal.ZERO;
        BigDecimal median;
        int middle = volumes.size() / 2;
        if (volumes.size() % 2 == 0) median = BigDecimal.valueOf(volumes.get(middle - 1))
                .add(BigDecimal.valueOf(volumes.get(middle))).divide(BigDecimal.valueOf(2), MC);
        else median = BigDecimal.valueOf(volumes.get(middle));
        return BigDecimal.valueOf(auctionVolume).divide(median, MC);
    }

    private static boolean between(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(HUNDRED, MC).divide(denominator, MC);
    }

    private static BigDecimal price(BigDecimal value) { return value == null ? null : value.setScale(2, HALF_UP); }
    private static BigDecimal value(BigDecimal value) { return value.setScale(4, HALF_UP); }
    private enum Trend { BULLISH, BEARISH, NEUTRAL }

    public record Policy(
            int minimumBars,
            BigDecimal minimumAbsoluteGapPercent,
            BigDecimal maximumAbsoluteGapPercent,
            BigDecimal maximumSpreadBps,
            BigDecimal minimumAuctionTurnover,
            BigDecimal longMinimumRsi,
            BigDecimal longMaximumRsi,
            BigDecimal shortMinimumRsi,
            BigDecimal shortMaximumRsi,
            BigDecimal atrStopMultiple,
            BigDecimal minimumStopPercent) {
        public Policy {
            if (minimumBars < 51) throw new IllegalArgumentException("Minimum bars must cover EMA50 warm-up");
            Objects.requireNonNull(minimumAbsoluteGapPercent); Objects.requireNonNull(maximumAbsoluteGapPercent);
            Objects.requireNonNull(maximumSpreadBps); Objects.requireNonNull(minimumAuctionTurnover);
            Objects.requireNonNull(longMinimumRsi);
            Objects.requireNonNull(longMaximumRsi); Objects.requireNonNull(shortMinimumRsi);
            Objects.requireNonNull(shortMaximumRsi); Objects.requireNonNull(atrStopMultiple);
            Objects.requireNonNull(minimumStopPercent);
        }

        public static Policy conservativeDefaults() {
            return new Policy(60, new BigDecimal("0.30"), new BigDecimal("2.50"), new BigDecimal("20"),
                    new BigDecimal("5000000"),
                    new BigDecimal("45"), new BigDecimal("75"), new BigDecimal("25"), new BigDecimal("55"),
                    new BigDecimal("0.50"), new BigDecimal("0.35"));
        }
    }
}
