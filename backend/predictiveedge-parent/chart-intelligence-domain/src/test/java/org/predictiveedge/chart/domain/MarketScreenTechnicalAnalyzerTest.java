package org.predictiveedge.chart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketScreenTechnicalAnalyzerTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-11T10:00:00Z");
    private final MarketScreenTechnicalAnalyzer analyzer = new MarketScreenTechnicalAnalyzer(
            MarketScreenTechnicalAnalyzer.Policy.conservativeDefaults());

    @Test
    void producesConfirmationGatedLongLevelsForQualifiedTopGainer() {
        var result = analyzer.analyze(observation(MarketScreenType.TOP_GAINERS, "2.75", "108"),
                bars(true, 80), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.BUY_CANDIDATE);
        assertThat(result.confirmationRule()).isEqualTo("BUY_ONLY_ABOVE_FIRST_5_MINUTE_HIGH_AND_SESSION_VWAP");
        assertThat(result.ema20()).isGreaterThan(result.ema50());
        assertThat(result.rsi14()).isBetween(new BigDecimal("45"), new BigDecimal("75"));
        assertThat(result.indicativeStop()).isLessThan(result.referenceEntry());
        assertThat(result.indicativeTargetOne()).isGreaterThan(result.referenceEntry());
        assertThat(result.indicativeTargetTwo()).isGreaterThan(result.indicativeTargetOne());
        assertThat(result.evidenceReferences()).hasSize(50);
    }

    @Test
    void producesConfirmationGatedShortLevelsForQualifiedTopLoser() {
        var result = analyzer.analyze(observation(MarketScreenType.TOP_LOSERS, "-2.75", "92"),
                bars(false, 80), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.SELL_CANDIDATE);
        assertThat(result.confirmationRule()).isEqualTo("SELL_ONLY_BELOW_FIRST_5_MINUTE_LOW_AND_SESSION_VWAP");
        assertThat(result.ema20()).isLessThan(result.ema50());
        assertThat(result.rsi14()).isBetween(new BigDecimal("25"), new BigDecimal("55"));
        assertThat(result.indicativeStop()).isGreaterThan(result.referenceEntry());
        assertThat(result.indicativeTargetOne()).isLessThan(result.referenceEntry());
        assertThat(result.indicativeTargetTwo()).isLessThan(result.indicativeTargetOne());
    }

    @Test
    void refusesLevelsWhenHistoricalEvidenceIsInsufficient() {
        var result = analyzer.analyze(observation(MarketScreenType.TOP_GAINERS, "2.75", "108"),
                bars(true, 20), CUTOFF);

        assertThat(result.action()).isEqualTo(IntradaySetupAction.INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmationRule()).isEqualTo("NO_ENTRY");
        assertThat(result.referenceEntry()).isNull();
        assertThat(result.indicativeStop()).isNull();
        assertThat(result.reasons()).singleElement().asString().contains("60 finalized five-minute bars");
    }

    private static MarketScreenObservation observation(MarketScreenType type, String change, String lastPrice) {
        BigDecimal ltp = new BigDecimal(lastPrice);
        return new MarketScreenObservation(type, "NSE", "TEST", "EQ", 1, ltp, ltp.add(BigDecimal.ONE),
                ltp.subtract(BigDecimal.ONE), new BigDecimal("100"), ltp, new BigDecimal(change), 1_000_000,
                new BigDecimal("108000000"), "", CUTOFF.minusSeconds(60));
    }

    private static List<IntradayHistoricalBar> bars(boolean rising, int count) {
        var result = new ArrayList<IntradayHistoricalBar>();
        Instant start = CUTOFF.minus(Duration.ofMinutes(5L * count));
        for (int index = 0; index < count; index++) {
            double trend = (rising ? 0.10 : -0.10) * index;
            double wave = index % 2 == 0 ? 0.30 : -0.30;
            BigDecimal close = BigDecimal.valueOf(100 + trend + wave).setScale(2, RoundingMode.HALF_UP);
            BigDecimal open = close.add(rising ? new BigDecimal("-0.05") : new BigDecimal("0.05"));
            Instant barStart = start.plus(Duration.ofMinutes(5L * index));
            Instant barEnd = barStart.plus(Duration.ofMinutes(5));
            result.add(new IntradayHistoricalBar(LocalDate.ofInstant(barStart, ZoneOffset.UTC), barStart, barEnd,
                    open, close.max(open).add(new BigDecimal("0.20")), close.min(open).subtract(new BigDecimal("0.20")),
                    close, 10_000L + index, barEnd, "ZERODHA:TEST:5m:" + index));
        }
        return List.copyOf(result);
    }
}
