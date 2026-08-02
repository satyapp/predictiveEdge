package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreEquityFeatureProfileTest {
    private static final ObservationSubject SUBJECT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE");

    @Test
    void registersTheFourteenExactProductionDefinitions() {
        var registry = CoreEquityFeatureProfile.definitions();

        assertThat(registry.size()).isEqualTo(14);
        assertThat(registry.require(CoreEquityFeatureProfile.EMA_20).inputRequirement().timeframe())
                .isEqualTo(BarTimeframe.FIFTEEN_MINUTES);
        assertThat(registry.require(CoreEquityFeatureProfile.RSI_14).inputRequirement().requiredBars())
                .isEqualTo(15);
        assertThat(registry.require(CoreEquityFeatureProfile.SESSION_VWAP_APPROX)
                .inputRequirement().resetsAtSessionBoundary()).isTrue();
    }

    @Test
    void calculatesProductionDirectionTrendQualityAndRegimeOnFinalFifteenMinuteBars() {
        var bars = bars(60, BarTimeframe.FIFTEEN_MINUTES, at("2026-07-20T03:45:00Z"), 100, 100);
        var manifest = manifest(bars, BarTimeframe.FIFTEEN_MINUTES);
        var definitions = CoreEquityFeatureProfile.definitions(); var calculators = CoreEquityFeatureProfile.calculators();
        var computedAt = manifest.cutoff().knowledgeCutoff();

        var ema20 = calculators.calculate(definitions.require(CoreEquityFeatureProfile.EMA_20), manifest, computedAt);
        var ema50 = calculators.calculate(definitions.require(CoreEquityFeatureProfile.EMA_50), manifest, computedAt);
        var plusDi = calculators.calculate(definitions.require(CoreEquityFeatureProfile.PLUS_DI_14), manifest, computedAt);
        var minusDi = calculators.calculate(definitions.require(CoreEquityFeatureProfile.MINUS_DI_14), manifest, computedAt);
        var adx = calculators.calculate(definitions.require(CoreEquityFeatureProfile.ADX_14), manifest, computedAt);
        var width = calculators.calculate(definitions.require(CoreEquityFeatureProfile.BOLLINGER_WIDTH_20), manifest, computedAt);

        assertThat(ema20.value()).isGreaterThan(ema50.value());
        assertThat(plusDi.value()).isEqualByComparingTo("50.000000");
        assertThat(minusDi.value()).isEqualByComparingTo("0.000000");
        assertThat(adx.value()).isEqualByComparingTo("100.000000");
        assertThat(width.value()).isPositive();
    }

    @Test
    void calculatesRsiAtrRelativeVolumeVwapAndPriorBarDonchianOnFiveMinuteBars() {
        var bars = bars(25, BarTimeframe.FIVE_MINUTES, at("2026-08-03T03:45:00Z"), 100, 100);
        var last = bars.getLast();
        bars.set(24, bar(24, BarTimeframe.FIVE_MINUTES, at("2026-08-03T03:45:00Z"),
                124, new BigDecimal("1000"), new BigDecimal("123"), 200,
                LocalDate.of(2026, 8, 3)));
        var manifest = manifest(bars, BarTimeframe.FIVE_MINUTES);
        var definitions = CoreEquityFeatureProfile.definitions(); var calculators = CoreEquityFeatureProfile.calculators();
        var computedAt = manifest.cutoff().knowledgeCutoff();

        var upper = calculators.calculate(definitions.require(CoreEquityFeatureProfile.DONCHIAN_UPPER_20), manifest, computedAt);
        var lower = calculators.calculate(definitions.require(CoreEquityFeatureProfile.DONCHIAN_LOWER_20), manifest, computedAt);
        var rsi = calculators.calculate(definitions.require(CoreEquityFeatureProfile.RSI_14), manifest, computedAt);
        var atr = calculators.calculate(definitions.require(CoreEquityFeatureProfile.ATR_14), manifest, computedAt);
        var relativeVolume = calculators.calculate(definitions.require(CoreEquityFeatureProfile.ROLLING_RVOL_20), manifest, computedAt);
        var vwap = calculators.calculate(definitions.require(CoreEquityFeatureProfile.SESSION_VWAP_APPROX), manifest, computedAt);

        assertThat(upper.value()).isEqualByComparingTo("124.000000");
        assertThat(lower.value()).isEqualByComparingTo("103.000000");
        assertThat(rsi.value()).isEqualByComparingTo("100.000000");
        assertThat(atr.value()).isPositive(); // The current spike is causally included in ATR, unlike Donchian.
        assertThat(relativeVolume.value()).isEqualByComparingTo("2.000000");
        assertThat(vwap.value()).isPositive();
        assertThat(last.key().interval()).isEqualTo(bars.getLast().key().interval());
    }

    @Test
    void sessionVwapIgnoresPriorSessionBars() {
        var prior = bar(0, BarTimeframe.FIVE_MINUTES, at("2026-08-02T03:45:00Z"),
                100, new BigDecimal("101"), new BigDecimal("99"), 100, LocalDate.of(2026, 8, 2));
        var current = bar(0, BarTimeframe.FIVE_MINUTES, at("2026-08-03T03:45:00Z"),
                200, new BigDecimal("201"), new BigDecimal("199"), 100, LocalDate.of(2026, 8, 3));
        var manifest = manifest(new ArrayList<>(List.of(prior, current)), BarTimeframe.FIVE_MINUTES);

        var value = CoreEquityFeatureProfile.calculators().calculate(
                CoreEquityFeatureProfile.definitions().require(CoreEquityFeatureProfile.SESSION_VWAP_APPROX),
                manifest, manifest.cutoff().knowledgeCutoff());

        assertThat(value.value()).isEqualByComparingTo("200.000000");
    }

    private static ArrayList<MarketBarRevision> bars(int count, BarTimeframe timeframe, Instant anchor,
            int initialClose, long volume) {
        var result = new ArrayList<MarketBarRevision>();
        for (int index = 0; index < count; index++) result.add(bar(index, timeframe, anchor, initialClose + index,
                BigDecimal.valueOf(initialClose + index + 1L), BigDecimal.valueOf(initialClose + index - 1L),
                volume, anchor.atZone(java.time.ZoneOffset.UTC).toLocalDate()));
        return result;
    }

    private static MarketBarRevision bar(int index, BarTimeframe timeframe, Instant anchor, int close,
            BigDecimal high, BigDecimal low, long volume, LocalDate date) {
        var start = anchor.plus(timeframe.duration().multipliedBy(index)); var end = start.plus(timeframe.duration());
        return new MarketBarRevision(new MarketBarKey(SUBJECT, new MarketSessionId("NSE", date, "REGULAR"),
                timeframe, new BarInterval(start, end, false)), 2,
                new MarketBarValues(BigDecimal.valueOf(close), high, low, BigDecimal.valueOf(close), volume),
                end, BarFinalityState.FINAL, end.plusSeconds(1), null, new ContentHash("f".repeat(64)),
                "ohlcv-v1", "finality-v1");
    }

    private static FeatureInputManifest manifest(List<MarketBarRevision> bars, BarTimeframe timeframe) {
        var last = bars.getLast();
        var cutoff = new EvaluationCutoff(last.key().interval().endsAt(), last.availableAt());
        return FeatureInputManifest.select(cutoff, SUBJECT, timeframe, bars);
    }

    private static Instant at(String value) { return Instant.parse(value); }
}
