package org.predictiveedge.chart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreOpenCandidateRankerTest {
    private final PreOpenCandidateRanker ranker = new PreOpenCandidateRanker();

    @Test
    void ranksGainersAndLosersSeparatelyUsingLiquidityAndParticipation() {
        var liquidLong = assessment("LIQUID", IntradaySetupAction.BUY_CANDIDATE,
                IntradaySetupType.GAP_CONTINUATION, "1.80", "500000000", "3.5", "2", "61", "103", "100", "2");
        var thinLong = assessment("THIN", IntradaySetupAction.BUY_CANDIDATE,
                IntradaySetupType.GAP_CONTINUATION, "2.00", "6000000", "0.4", "18", "72", "101", "100", "2");
        var liquidShort = assessment("SHORT", IntradaySetupAction.SELL_CANDIDATE,
                IntradaySetupType.GAP_CONTINUATION, "-1.60", "250000000", "2.8", "4", "39", "97", "100", "2");

        var result = ranker.rank(List.of(thinLong, liquidShort, liquidLong), 5);

        assertThat(result.calibratedProbability()).isFalse();
        assertThat(result.scoreVersion()).isEqualTo(PreOpenCandidateRanker.SCORE_VERSION);
        assertThat(result.probableGainers()).extracting(PreOpenCandidateRanker.RankedCandidate::symbol)
                .containsExactly("LIQUID", "THIN");
        assertThat(result.probableGainers().getFirst().rankingScore())
                .isGreaterThan(result.probableGainers().get(1).rankingScore());
        assertThat(result.probableLosers()).singleElement().satisfies(value -> {
            assertThat(value.symbol()).isEqualTo("SHORT");
            assertThat(value.direction()).isEqualTo(PreOpenCandidateRanker.Direction.LOSER);
            assertThat(value.rank()).isEqualTo(1);
        });
    }

    @Test
    void excludesNonDirectionalAssessmentsAndCapsEachLeaderboard() {
        var wait = assessment("WAIT", IntradaySetupAction.WAIT, IntradaySetupType.NONE,
                "0.10", "500000000", "3", "1", "50", "100", "100", "2");
        var first = assessment("FIRST", IntradaySetupAction.BUY_CANDIDATE, IntradaySetupType.GAP_CONTINUATION,
                "2.00", "500000000", "3", "1", "60", "102", "100", "2");
        var second = assessment("SECOND", IntradaySetupAction.BUY_CANDIDATE, IntradaySetupType.GAP_CONTINUATION,
                "1.00", "50000000", "2", "5", "58", "101", "100", "2");

        var result = ranker.rank(List.of(wait, second, first), 1);

        assertThat(result.evaluatedAssessments()).isEqualTo(3);
        assertThat(result.probableGainers()).singleElement()
                .extracting(PreOpenCandidateRanker.RankedCandidate::symbol).isEqualTo("FIRST");
        assertThat(result.probableLosers()).isEmpty();
    }

    private static IntradaySetupAssessment assessment(String symbol, IntradaySetupAction action,
            IntradaySetupType type, String gap, String turnover, String volumeRatio, String spread, String rsi,
            String ema20, String ema50, String atr) {
        return new IntradaySetupAssessment("NSE", symbol, action, type, "CONFIRM", bd(gap), bd(ema20), bd(ema50),
                bd(rsi), bd(atr), bd(spread), bd(turnover), bd(volumeRatio), bd("100"), bd("99"), bd("101"),
                bd("102"), Instant.parse("2026-11-11T03:40:00Z"), List.of("test"), List.of("evidence"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
