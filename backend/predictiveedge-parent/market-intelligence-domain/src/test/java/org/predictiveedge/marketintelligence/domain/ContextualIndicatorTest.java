package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextualIndicatorTest {
    private static final ObservationSubject INSTRUMENT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE");
    private static final ObservationSubject BENCHMARK =
            new ObservationSubject(ObservationSubjectType.INDEX, "NSE:NIFTY50");

    @Test
    void calculatesRelativeLeadershipOnlyFromSynchronizedFinalSeries() {
        var primary = bars(INSTRUMENT, 100, 1);
        var comparison = bars(BENCHMARK, 100, 0);
        var cutoff = cutoff(primary.getLast());
        var synchronizedBars = SynchronizedBarManifest.create(
                FeatureInputManifest.select(cutoff, INSTRUMENT, BarTimeframe.FIFTEEN_MINUTES, primary),
                FeatureInputManifest.select(cutoff, BENCHMARK, BarTimeframe.FIFTEEN_MINUTES, comparison));

        var value = new RelativeLeadershipCalculator(CoreEquityFeatureProfile.RELATIVE_LEADERSHIP_20, 20)
                .calculate(CoreEquityFeatureProfile.definitions().require(
                        CoreEquityFeatureProfile.RELATIVE_LEADERSHIP_20), synchronizedBars,
                        cutoff.knowledgeCutoff());

        assertThat(value.value()).isEqualByComparingTo("19.000000");
        assertThat(value.subject()).isEqualTo(INSTRUMENT);
    }

    @Test
    void timeAdjustedRelativeVolumeUsesKnowledgeDatedSameSlotBaseline() {
        var current = bar(INSTRUMENT, 0, BarTimeframe.FIVE_MINUTES, 100, 200,
                at("2026-08-03T03:45:00Z"));
        var cutoff = cutoff(current);
        var baseline = new HistoricalVolumeBaseline("NSE", BarTimeframe.FIVE_MINUTES, 0,
                100, 40, at("2026-08-03T03:00:00Z"), "median-40-sessions-v1");

        var value = new TimeAdjustedRelativeVolumeCalculator(CoreEquityFeatureProfile.TIME_ADJUSTED_RVOL)
                .calculate(CoreEquityFeatureProfile.definitions().require(CoreEquityFeatureProfile.TIME_ADJUSTED_RVOL),
                        current, 0, baseline, cutoff, cutoff.knowledgeCutoff());

        assertThat(value.value()).isEqualByComparingTo("2.000000");
    }

    @Test
    void breadthPreservesPointInTimeUniverseCoverageAndCounts() {
        var cutoff = new EvaluationCutoff(at("2026-08-03T04:00:00Z"), at("2026-08-03T04:00:01Z"));
        var constituents = List.of(
                constituent("NSE:A", 100, 101), constituent("NSE:B", 100, 99), constituent("NSE:C", 100, 100));

        var breadth = AdvanceDeclineSnapshot.calculate("NSE-MVP", "universe-v7", cutoff, 4,
                List.of(new CoverageExclusion("Suspended", 1)), constituents,
                new ContentHash("1".repeat(64)));

        assertThat(breadth.advances()).isEqualTo(1);
        assertThat(breadth.declines()).isEqualTo(1);
        assertThat(breadth.unchanged()).isEqualTo(1);
        assertThat(breadth.netBreadthPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(breadth.coverage().expectedCount()).isEqualTo(4);
    }

    @Test
    void vixContextUsesExternalPointInTimeLevelAndGovernedThresholds() {
        var cutoff = new EvaluationCutoff(at("2026-08-03T04:00:00Z"), at("2026-08-03T04:00:01Z"));
        var context = VolatilityIndexContext.classify(new BigDecimal("22"), new BigDecimal("20"),
                at("2026-08-03T04:00:00Z"), at("2026-08-03T04:00:01Z"), cutoff,
                new BigDecimal("12"), new BigDecimal("18"), new BigDecimal("25"), "vix-levels-v1",
                new ContentHash("2".repeat(64)));

        assertThat(context.state()).isEqualTo(MarketStressState.ELEVATED);
        assertThat(context.changePercent()).isEqualByComparingTo("10");
    }

    @Test
    void contextualInputsFailClosedWhenNotKnownAtTheCutoff() {
        var cutoff = new EvaluationCutoff(at("2026-08-03T04:00:00Z"), at("2026-08-03T04:00:00Z"));
        assertThatThrownBy(() -> VolatilityIndexContext.classify(new BigDecimal("22"), new BigDecimal("20"),
                at("2026-08-03T03:59:59Z"), at("2026-08-03T04:00:01Z"), cutoff,
                new BigDecimal("12"), new BigDecimal("18"), new BigDecimal("25"), "v1",
                new ContentHash("3".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not causally eligible");
    }

    private static ArrayList<MarketBarRevision> bars(ObservationSubject subject, int initial, int increment) {
        var values = new ArrayList<MarketBarRevision>();
        for (int index = 0; index < 20; index++) values.add(bar(subject, index, BarTimeframe.FIFTEEN_MINUTES,
                initial + index * increment, 100, at("2026-08-01T03:45:00Z")));
        return values;
    }

    private static MarketBarRevision bar(ObservationSubject subject, int index, BarTimeframe timeframe,
            int close, long volume, Instant anchor) {
        var start = anchor.plus(timeframe.duration().multipliedBy(index)); var end = start.plus(timeframe.duration());
        return new MarketBarRevision(new MarketBarKey(subject,
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 1), "REGULAR"), timeframe,
                new BarInterval(start, end, false)), 2,
                new MarketBarValues(BigDecimal.valueOf(close), BigDecimal.valueOf(close + 1L),
                        BigDecimal.valueOf(close - 1L), BigDecimal.valueOf(close), volume),
                end, BarFinalityState.FINAL, end.plusSeconds(1), null, new ContentHash("4".repeat(64)),
                "ohlcv-v1", "finality-v1");
    }

    private static BreadthConstituent constituent(String id, int prior, int current) {
        return new BreadthConstituent(new ObservationSubject(ObservationSubjectType.INSTRUMENT, id),
                BigDecimal.valueOf(prior), BigDecimal.valueOf(current), at("2026-08-03T04:00:00Z"),
                at("2026-08-03T04:00:01Z"));
    }

    private static EvaluationCutoff cutoff(MarketBarRevision last) {
        return new EvaluationCutoff(last.key().interval().endsAt(), last.availableAt());
    }

    private static Instant at(String value) { return Instant.parse(value); }
}
