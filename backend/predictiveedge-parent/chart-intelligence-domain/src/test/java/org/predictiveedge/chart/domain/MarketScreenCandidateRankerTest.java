package org.predictiveedge.chart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketScreenCandidateRankerTest {
    @Test
    void ranksStrongLiquidGainerAheadOfWeakerCandidateWithoutClaimingProbability() {
        var ranker = new MarketScreenCandidateRanker();
        var result = ranker.rank(List.of(
                observation("STRONG", 1, "4.8", 5_000_000, "5000000000", "100", "105", "104.8"),
                observation("WEAK", 2, "1.2", 100_000, "100000000", "100", "104", "101")));

        assertThat(result.calibratedProbability()).isFalse();
        assertThat(result.scoreVersion()).isEqualTo("market-screen-v1");
        assertThat(result.candidates()).extracting(MarketScreenCandidateRanker.RankedCandidate::symbol)
                .containsExactly("STRONG", "WEAK");
        assertThat(result.candidates().getFirst().stance()).isEqualTo("LONG_WATCH");
        assertThat(result.candidates().getFirst().score()).isGreaterThan(result.candidates().getLast().score());
    }

    private static MarketScreenObservation observation(String symbol, int rank, String change, long volume,
            String turnover, String low, String high, String ltp) {
        return new MarketScreenObservation(MarketScreenType.TOP_GAINERS, "NSE", symbol, "EQ", rank,
                bd("101"), bd(high), bd(low), bd("100"), bd(ltp), bd(change), volume, bd(turnover), "",
                Instant.parse("2026-11-11T10:00:00Z"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
