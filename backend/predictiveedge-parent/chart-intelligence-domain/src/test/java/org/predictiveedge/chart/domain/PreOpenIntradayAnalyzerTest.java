package org.predictiveedge.chart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreOpenIntradayAnalyzerTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-09T03:40:00Z");
    private final PreOpenIntradayAnalyzer analyzer = new PreOpenIntradayAnalyzer(new PreOpenIntradayAnalyzer.Policy(
            60, bd("0.30"), bd("2.50"), bd("20"), bd("5000000"), bd("40"), bd("100"), bd("0"), bd("60"),
            bd("0.50"), bd("0.35")));

    @Test
    void emitsConfirmationGatedBuyCandidateWhenGapAndTrendAlign() {
        IntradaySetupAssessment result = analyzer.analyze(snapshot("EQ", "100", "101"),
                bars(true, 60), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.BUY_CANDIDATE);
        assertThat(result.setupType()).isEqualTo(IntradaySetupType.GAP_CONTINUATION);
        assertThat(result.confirmationRule()).contains("15_MINUTE_HIGH").contains("VWAP");
        assertThat(result.ema20()).isGreaterThan(result.ema50());
        assertThat(result.indicativeStop()).isLessThan(result.referenceEntry());
        assertThat(result.indicativeTargetTwo()).isGreaterThan(result.indicativeTargetOne());
        assertThat(result.auctionVolumeRatio()).isGreaterThan(BigDecimal.ONE);
        assertThat(result.evidenceReferences()).hasSize(50);
    }

    @Test
    void emitsConfirmationGatedSellCandidateWhenNegativeGapAndTrendAlign() {
        IntradaySetupAssessment result = analyzer.analyze(snapshot("EQ", "100", "99"),
                bars(false, 60), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.SELL_CANDIDATE);
        assertThat(result.setupType()).isEqualTo(IntradaySetupType.GAP_CONTINUATION);
        assertThat(result.confirmationRule()).contains("15_MINUTE_LOW").contains("VWAP");
        assertThat(result.ema20()).isLessThan(result.ema50());
        assertThat(result.indicativeStop()).isGreaterThan(result.referenceEntry());
    }

    @Test
    void convertsGapUpAgainstBearishHistoryIntoFailedGapCandidate() {
        IntradaySetupAssessment result = analyzer.analyze(snapshot("EQ", "100", "101"),
                bars(false, 60), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.SELL_CANDIDATE);
        assertThat(result.setupType()).isEqualTo(IntradaySetupType.FAILED_GAP_REVERSAL);
        assertThat(result.referenceEntry()).isEqualByComparingTo("100.00");
        assertThat(result.confirmationRule()).contains("PREVIOUS_CLOSE");
    }

    @Test
    void failsClosedForTradeToTradeSeriesOrInsufficientHistory() {
        IntradaySetupAssessment blocked = analyzer.analyze(snapshot("BE", "100", "101"),
                bars(true, 60), CUTOFF);
        IntradaySetupAssessment warmingUp = analyzer.analyze(snapshot("EQ", "100", "101"),
                bars(true, 59), CUTOFF);

        assertThat(blocked.action()).isEqualTo(IntradaySetupAction.NO_TRADE);
        assertThat(blocked.reasons()).anyMatch(reason -> reason.contains("EQ-series"));
        assertThat(warmingUp.action()).isEqualTo(IntradaySetupAction.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void ignoresBarsThatWereNotCausallyAvailableAtTheCutoff() {
        var history = new ArrayList<>(bars(true, 60));
        history.add(bar(60, bd("10"), CUTOFF.plusSeconds(300)));

        IntradaySetupAssessment result = analyzer.analyze(snapshot("EQ", "100", "101"), history, CUTOFF);

        assertThat(result.evidenceReferences()).doesNotContain("bar-60");
        assertThat(result.action()).isEqualTo(IntradaySetupAction.BUY_CANDIDATE);
    }

    private static PreOpenSnapshot snapshot(String series, String previousClose, String auctionPrice) {
        return new PreOpenSnapshot("NSE", "SBIN", series, bd(previousClose), bd(auctionPrice), 100_000,
                bd("100000000"), bd(auctionPrice).subtract(bd("0.05")),
                bd(auctionPrice).add(bd("0.05")), CUTOFF.minusSeconds(30));
    }

    private static List<IntradayHistoricalBar> bars(boolean rising, int count) {
        var values = new ArrayList<IntradayHistoricalBar>();
        for (int index = 0; index < count; index++) {
            BigDecimal price = rising ? bd("90").add(bd("0.20").multiply(BigDecimal.valueOf(index)))
                    : bd("110").subtract(bd("0.20").multiply(BigDecimal.valueOf(index)));
            values.add(bar(index, price, null));
        }
        return values;
    }

    private static IntradayHistoricalBar bar(int index, BigDecimal close, Instant forcedAvailability) {
        Instant start = CUTOFF.minusSeconds((long) (61 - index) * 300);
        Instant end = start.plusSeconds(300);
        BigDecimal open = close.subtract(bd("0.05"));
        return new IntradayHistoricalBar(LocalDate.ofInstant(start, ZoneOffset.UTC), start, end, open,
                close.add(bd("0.10")), open.subtract(bd("0.10")), close, 10_000 + index,
                forcedAvailability == null ? end : forcedAvailability, "bar-" + index);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
