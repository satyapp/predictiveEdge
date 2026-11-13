package org.predictiveedge.chart.domain;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic within-file triage score. It is not a win probability or final trade recommendation. */
public final class MarketScreenCandidateRanker {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public RankedScreen rank(List<MarketScreenObservation> observations) {
        Objects.requireNonNull(observations, "Screen observations are required");
        if (observations.isEmpty()) return new RankedScreen("market-screen-v1", false, List.of());
        MarketScreenType type = observations.getFirst().screenType();
        if (type != MarketScreenType.TOP_GAINERS && type != MarketScreenType.TOP_LOSERS)
            throw new IllegalArgumentException("The v1 ranker currently supports top gainers and top losers only");
        if (observations.stream().anyMatch(value -> value.screenType() != type))
            throw new IllegalArgumentException("A ranked screen cannot mix source types");

        var ranked = new ArrayList<RankedCandidate>();
        for (MarketScreenObservation value : observations) {
            validateDirection(value);
            BigDecimal move = value.percentChange().abs().divide(BigDecimal.valueOf(5), MC)
                    .min(BigDecimal.ONE).multiply(BigDecimal.valueOf(45), MC);
            BigDecimal range = value.high().subtract(value.low());
            BigDecimal favorableClose = range.signum() == 0 ? new BigDecimal("0.50")
                    : type == MarketScreenType.TOP_GAINERS
                        ? value.lastPrice().subtract(value.low()).divide(range, MC)
                        : value.high().subtract(value.lastPrice()).divide(range, MC);
            BigDecimal close = favorableClose.max(BigDecimal.ZERO).min(BigDecimal.ONE)
                    .multiply(BigDecimal.valueOf(20), MC);
            BigDecimal volume = percentile(value.volume(), observations.stream().map(MarketScreenObservation::volume).toList())
                    .multiply(BigDecimal.valueOf(15), MC);
            BigDecimal turnover = percentile(value.turnover(), observations.stream().map(MarketScreenObservation::turnover).toList())
                    .multiply(BigDecimal.valueOf(20), MC);
            BigDecimal score = move.add(close).add(volume).add(turnover).setScale(2, HALF_UP);
            List<String> reasons = List.of(
                    "Absolute move contributed " + move.setScale(1, HALF_UP) + " of 45 points",
                    "Favorable close location contributed " + close.setScale(1, HALF_UP) + " of 20 points",
                    "Within-file volume rank contributed " + volume.setScale(1, HALF_UP) + " of 15 points",
                    "Within-file turnover rank contributed " + turnover.setScale(1, HALF_UP) + " of 20 points");
            ranked.add(new RankedCandidate(0, value.symbol(),
                    type == MarketScreenType.TOP_GAINERS ? "LONG_WATCH" : "SHORT_WATCH",
                    score, band(score), value.percentChange(), value.lastPrice(), value.volume(), value.turnover(),
                    value.sourceRank(), reasons));
        }
        ranked.sort(Comparator.comparing(RankedCandidate::score).reversed()
                .thenComparing(RankedCandidate::sourceRank).thenComparing(RankedCandidate::symbol));
        var numbered = new ArrayList<RankedCandidate>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedCandidate value = ranked.get(index);
            numbered.add(new RankedCandidate(index + 1, value.symbol(), value.stance(), value.score(),
                    value.scoreBand(), value.percentChange(), value.lastPrice(), value.volume(), value.turnover(),
                    value.sourceRank(), value.reasons()));
        }
        return new RankedScreen("market-screen-v1", false, List.copyOf(numbered));
    }

    private static void validateDirection(MarketScreenObservation value) {
        if (value.screenType() == MarketScreenType.TOP_GAINERS && value.percentChange().signum() <= 0)
            throw new IllegalArgumentException("Top-gainer row must have positive %CHANGE: " + value.symbol());
        if (value.screenType() == MarketScreenType.TOP_LOSERS && value.percentChange().signum() >= 0)
            throw new IllegalArgumentException("Top-loser row must have negative %CHANGE: " + value.symbol());
    }

    private static BigDecimal percentile(long value, List<Long> values) {
        long atOrBelow = values.stream().filter(candidate -> candidate <= value).count();
        return BigDecimal.valueOf(atOrBelow).divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static BigDecimal percentile(BigDecimal value, List<BigDecimal> values) {
        long atOrBelow = values.stream().filter(candidate -> candidate.compareTo(value) <= 0).count();
        return BigDecimal.valueOf(atOrBelow).divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static String band(BigDecimal score) {
        return score.compareTo(BigDecimal.valueOf(75)) >= 0 ? "HIGH"
                : score.compareTo(BigDecimal.valueOf(55)) >= 0 ? "MEDIUM" : "LOW";
    }

    public record RankedScreen(String scoreVersion, boolean calibratedProbability,
            List<RankedCandidate> candidates) { }

    public record RankedCandidate(int rank, String symbol, String stance, BigDecimal score, String scoreBand,
            BigDecimal percentChange, BigDecimal lastPrice, long volume, BigDecimal turnover, int sourceRank,
            List<String> reasons) { }
}
