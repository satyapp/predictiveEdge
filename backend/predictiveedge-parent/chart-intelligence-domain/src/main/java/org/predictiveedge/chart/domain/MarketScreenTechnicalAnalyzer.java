package org.predictiveedge.chart.domain;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Confirmation-gated technical analysis for NSE top-gainer/top-loser observations. */
public final class MarketScreenTechnicalAnalyzer {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final Policy policy;

    public MarketScreenTechnicalAnalyzer(Policy policy) { this.policy = Objects.requireNonNull(policy); }

    public MarketScreenTechnicalAssessment analyze(MarketScreenObservation observation,
            List<IntradayHistoricalBar> suppliedBars, Instant cutoff) {
        Objects.requireNonNull(observation, "Observation is required");
        Objects.requireNonNull(suppliedBars, "Historical bars are required");
        Objects.requireNonNull(cutoff, "Analysis cutoff is required");
        if (observation.observedAt().isAfter(cutoff))
            throw new IllegalArgumentException("Screen observation is not available at the analysis cutoff");
        List<IntradayHistoricalBar> bars = suppliedBars.stream()
                .filter(value -> !value.endsAt().isAfter(cutoff) && !value.availableAt().isAfter(cutoff))
                .sorted(Comparator.comparing(IntradayHistoricalBar::startsAt)).toList();
        if (bars.size() < policy.minimumBars()) return empty(observation, cutoff,
                "At least " + policy.minimumBars() + " finalized five-minute bars are required");

        List<BigDecimal> closes = bars.stream().map(IntradayHistoricalBar::close).toList();
        BigDecimal ema20 = ema(closes, 20), ema50 = ema(closes, 50);
        BigDecimal rsi14 = rsi(closes, 14), atr14 = atr(bars, 14);
        BigDecimal lastClose = closes.getLast();
        boolean bullish = lastClose.compareTo(ema20) > 0 && ema20.compareTo(ema50) > 0;
        boolean bearish = lastClose.compareTo(ema20) < 0 && ema20.compareTo(ema50) < 0;
        boolean gainer = observation.screenType() == MarketScreenType.TOP_GAINERS;
        boolean qualified = gainer ? bullish && between(rsi14, policy.longMinimumRsi(), policy.longMaximumRsi())
                : bearish && between(rsi14, policy.shortMinimumRsi(), policy.shortMaximumRsi());
        IntradaySetupAction action = qualified
                ? gainer ? IntradaySetupAction.BUY_CANDIDATE : IntradaySetupAction.SELL_CANDIDATE
                : IntradaySetupAction.WAIT;
        String confirmation = qualified
                ? gainer ? "BUY_ONLY_ABOVE_FIRST_5_MINUTE_HIGH_AND_SESSION_VWAP"
                        : "SELL_ONLY_BELOW_FIRST_5_MINUTE_LOW_AND_SESSION_VWAP"
                : "WAIT_FOR_FIRST_FINAL_5_MINUTE_BAR";
        BigDecimal entry = qualified ? observation.lastPrice() : null;
        BigDecimal stop = null, targetOne = null, targetTwo = null;
        var reasons = new ArrayList<String>();
        if (qualified) {
            BigDecimal risk = atr14.multiply(policy.atrStopMultiple(), MC)
                    .max(entry.multiply(policy.minimumStopPercent(), MC).divide(HUNDRED, MC));
            if (gainer) {
                stop = price(entry.subtract(risk)); targetOne = price(entry.add(risk));
                targetTwo = price(entry.add(risk.multiply(BigDecimal.valueOf(2), MC)));
                reasons.add("Top-gainer direction aligns with bullish EMA20/EMA50 structure");
            } else {
                stop = price(entry.add(risk)); targetOne = price(entry.subtract(risk));
                targetTwo = price(entry.subtract(risk.multiply(BigDecimal.valueOf(2), MC)));
                reasons.add("Top-loser direction aligns with bearish EMA20/EMA50 structure");
            }
            reasons.add("RSI14 is inside the governed directional momentum range");
            reasons.add("ATR levels remain indicative until opening-range and VWAP confirmation");
        } else {
            reasons.add("Screen direction is not confirmed by EMA20/EMA50 trend and governed RSI14 momentum");
        }
        if (!observation.corporateAction().isBlank())
            reasons.add("NSE source flags a corporate-action value: " + observation.corporateAction());
        List<String> evidence = bars.subList(Math.max(0, bars.size() - 50), bars.size()).stream()
                .map(IntradayHistoricalBar::evidenceReference).distinct().toList();
        return new MarketScreenTechnicalAssessment(observation.venue(), observation.symbol(), action, confirmation,
                value(observation.percentChange()), price(ema20), price(ema50), value(rsi14), price(atr14),
                observation.volume(), observation.turnover().setScale(2, HALF_UP), price(entry), stop, targetOne,
                targetTwo, cutoff, reasons, evidence);
    }

    private static MarketScreenTechnicalAssessment empty(MarketScreenObservation value, Instant cutoff, String reason) {
        return new MarketScreenTechnicalAssessment(value.venue(), value.symbol(),
                IntradaySetupAction.INSUFFICIENT_EVIDENCE, "NO_ENTRY", value(value.percentChange()), null, null,
                null, null, value.volume(), value.turnover().setScale(2, HALF_UP), null, null, null, null,
                cutoff, List.of(reason), List.of());
    }

    private static BigDecimal ema(List<BigDecimal> values, int period) {
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
            gains = gains.add(change.max(BigDecimal.ZERO)); losses = losses.add(change.negate().max(BigDecimal.ZERO));
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
        BigDecimal total = BigDecimal.ZERO;
        for (int index = bars.size() - period; index < bars.size(); index++) {
            var current = bars.get(index); BigDecimal prior = bars.get(index - 1).close();
            total = total.add(current.high().subtract(current.low())
                    .max(current.high().subtract(prior).abs()).max(current.low().subtract(prior).abs()));
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }

    private static boolean between(BigDecimal value, BigDecimal low, BigDecimal high) {
        return value.compareTo(low) >= 0 && value.compareTo(high) <= 0;
    }
    private static BigDecimal price(BigDecimal value) { return value == null ? null : value.setScale(2, HALF_UP); }
    private static BigDecimal value(BigDecimal value) { return value.setScale(4, HALF_UP); }

    public record Policy(int minimumBars, BigDecimal longMinimumRsi, BigDecimal longMaximumRsi,
            BigDecimal shortMinimumRsi, BigDecimal shortMaximumRsi, BigDecimal atrStopMultiple,
            BigDecimal minimumStopPercent) {
        public Policy { if (minimumBars < 51) throw new IllegalArgumentException("Minimum bars must cover EMA50"); }
        public static Policy conservativeDefaults() {
            return new Policy(60, new BigDecimal("45"), new BigDecimal("75"), new BigDecimal("25"),
                    new BigDecimal("55"), new BigDecimal("0.50"), new BigDecimal("0.35"));
        }
    }
}
