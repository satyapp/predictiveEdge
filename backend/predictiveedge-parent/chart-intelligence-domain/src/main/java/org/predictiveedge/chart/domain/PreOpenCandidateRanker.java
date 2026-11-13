package org.predictiveedge.chart.domain;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Explainable cross-sectional ranking for confirmation-gated pre-open candidates.
 * The score is a relative research score, not a calibrated probability or trade instruction.
 */
public final class PreOpenCandidateRanker {
    public static final String SCORE_VERSION = "PREOPEN_HEURISTIC_V1";
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal MINIMUM_TURNOVER = new BigDecimal("5000000");
    private static final BigDecimal MAXIMUM_TURNOVER = new BigDecimal("500000000");
    private static final BigDecimal MAXIMUM_GAP = new BigDecimal("2.50");
    private static final BigDecimal MAXIMUM_SPREAD = new BigDecimal("20");
    private static final BigDecimal FULL_PARTICIPATION_RATIO = new BigDecimal("3.0");

    public RankedUniverse rank(List<IntradaySetupAssessment> assessments, int maximumPerSide) {
        Objects.requireNonNull(assessments, "Assessments are required");
        if (maximumPerSide < 1 || maximumPerSide > 25)
            throw new IllegalArgumentException("Maximum rankings per side must be between 1 and 25");

        var gainers = new ArrayList<Unranked>();
        var losers = new ArrayList<Unranked>();
        for (IntradaySetupAssessment assessment : assessments) {
            Objects.requireNonNull(assessment, "Assessment is required");
            if (assessment.action() == IntradaySetupAction.BUY_CANDIDATE)
                gainers.add(score(assessment, Direction.GAINER));
            else if (assessment.action() == IntradaySetupAction.SELL_CANDIDATE)
                losers.add(score(assessment, Direction.LOSER));
        }

        Comparator<Unranked> order = Comparator.comparing(Unranked::score).reversed()
                .thenComparing(value -> value.assessment().auctionTurnover(), Comparator.reverseOrder())
                .thenComparing(value -> value.assessment().symbol());
        gainers.sort(order);
        losers.sort(order);
        return new RankedUniverse(SCORE_VERSION, false, assessments.size(),
                ranked(gainers, maximumPerSide), ranked(losers, maximumPerSide));
    }

    private static List<RankedCandidate> ranked(List<Unranked> values, int maximum) {
        var result = new ArrayList<RankedCandidate>();
        for (int index = 0; index < Math.min(maximum, values.size()); index++) {
            Unranked value = values.get(index);
            IntradaySetupAssessment assessment = value.assessment();
            result.add(new RankedCandidate(index + 1, assessment.venue(), assessment.symbol(), value.direction(),
                    value.score(), band(value.score()), assessment.action(), assessment.setupType(),
                    assessment.gapPercent(), value.components(), value.reasons()));
        }
        return List.copyOf(result);
    }

    private static Unranked score(IntradaySetupAssessment assessment, Direction direction) {
        BigDecimal gap = component(assessment.gapPercent().abs().divide(MAXIMUM_GAP, MC), 25);
        BigDecimal liquidity = component(logScale(assessment.auctionTurnover()), 15);
        BigDecimal participation = assessment.auctionVolumeRatio() == null ? ZERO
                : component(assessment.auctionVolumeRatio().divide(FULL_PARTICIPATION_RATIO, MC), 15);
        BigDecimal spread = component(ONE.subtract(assessment.spreadBps().divide(MAXIMUM_SPREAD, MC)), 10);

        boolean continuation = assessment.setupType() == IntradaySetupType.GAP_CONTINUATION;
        boolean directionAligned = direction == Direction.GAINER && assessment.gapPercent().signum() > 0
                || direction == Direction.LOSER && assessment.gapPercent().signum() < 0;
        BigDecimal setup = continuation && directionAligned ? new BigDecimal("15")
                : assessment.setupType() == IntradaySetupType.FAILED_GAP_REVERSAL ? new BigDecimal("7") : ZERO;

        BigDecimal trendStrength = ZERO;
        if (assessment.ema20() != null && assessment.ema50() != null
                && assessment.atr14() != null && assessment.atr14().signum() > 0) {
            BigDecimal emaSeparationInAtr = assessment.ema20().subtract(assessment.ema50()).abs()
                    .divide(assessment.atr14(), MC);
            trendStrength = component(emaSeparationInAtr.divide(new BigDecimal("2"), MC), 10);
        }

        BigDecimal momentum = momentum(assessment.rsi14(), direction);
        var components = new ComponentScores(gap, liquidity, participation, spread, setup, trendStrength, momentum);
        BigDecimal score = gap.add(liquidity).add(participation).add(spread).add(setup)
                .add(trendStrength).add(momentum).min(new BigDecimal("100")).setScale(2, HALF_UP);

        var reasons = new ArrayList<String>();
        reasons.add("Auction gap contribution " + gap + "/25");
        reasons.add("Auction liquidity contribution " + liquidity + "/15");
        reasons.add("Relative auction participation contribution " + participation + "/15");
        reasons.add(continuation && directionAligned
                ? "Gap direction and deterministic continuation setup align"
                : "Reversal setup receives a lower pre-open leadership weight");
        reasons.add("Opening-range and session-VWAP confirmation remains mandatory");
        return new Unranked(assessment, direction, score, components, reasons);
    }

    private static BigDecimal momentum(BigDecimal rsi, Direction direction) {
        if (rsi == null) return ZERO;
        BigDecimal center = direction == Direction.GAINER ? new BigDecimal("60") : new BigDecimal("40");
        BigDecimal quality = ONE.subtract(rsi.subtract(center).abs().divide(new BigDecimal("20"), MC));
        return component(quality, 10);
    }

    private static BigDecimal logScale(BigDecimal turnover) {
        if (turnover == null || turnover.compareTo(MINIMUM_TURNOVER) <= 0) return ZERO;
        if (turnover.compareTo(MAXIMUM_TURNOVER) >= 0) return ONE;
        double numerator = Math.log10(turnover.divide(MINIMUM_TURNOVER, MC).doubleValue());
        double denominator = Math.log10(MAXIMUM_TURNOVER.divide(MINIMUM_TURNOVER, MC).doubleValue());
        return BigDecimal.valueOf(numerator / denominator);
    }

    private static BigDecimal component(BigDecimal ratio, int maximum) {
        BigDecimal bounded = ratio.max(ZERO).min(ONE);
        return bounded.multiply(BigDecimal.valueOf(maximum), MC).setScale(2, HALF_UP);
    }

    private static String band(BigDecimal score) {
        if (score.compareTo(new BigDecimal("75")) >= 0) return "HIGH";
        if (score.compareTo(new BigDecimal("60")) >= 0) return "MEDIUM";
        return "LOW";
    }

    public enum Direction { GAINER, LOSER }

    public record ComponentScores(
            BigDecimal gapMomentum,
            BigDecimal auctionLiquidity,
            BigDecimal relativeParticipation,
            BigDecimal spreadQuality,
            BigDecimal setupAlignment,
            BigDecimal trendStrength,
            BigDecimal momentumQuality) { }

    public record RankedCandidate(
            int rank,
            String venue,
            String symbol,
            Direction direction,
            BigDecimal rankingScore,
            String scoreBand,
            IntradaySetupAction action,
            IntradaySetupType setupType,
            BigDecimal gapPercent,
            ComponentScores components,
            List<String> reasons) {
        public RankedCandidate {
            reasons = List.copyOf(Objects.requireNonNull(reasons, "Reasons are required"));
        }
    }

    public record RankedUniverse(
            String scoreVersion,
            boolean calibratedProbability,
            int evaluatedAssessments,
            List<RankedCandidate> probableGainers,
            List<RankedCandidate> probableLosers) {
        public RankedUniverse {
            probableGainers = List.copyOf(probableGainers);
            probableLosers = List.copyOf(probableLosers);
        }
    }

    private record Unranked(
            IntradaySetupAssessment assessment,
            Direction direction,
            BigDecimal score,
            ComponentScores components,
            List<String> reasons) { }
}
