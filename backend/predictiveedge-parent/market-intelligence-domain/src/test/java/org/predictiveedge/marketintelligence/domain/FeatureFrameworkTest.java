package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class FeatureFrameworkTest {
    private static final ObservationSubject SUBJECT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE");
    private static final ContentHash SOURCE_MANIFEST = new ContentHash("c".repeat(64));

    @Test
    void reportsUnavailableWarmingUpAndReadyWithoutInventingZero() {
        var definition = FeatureRegistryTest.definition("1.0.0");
        var cutoff = cutoff("2026-08-03T04:00:00Z", "2026-08-03T04:00:02Z");

        var unavailable = FeatureInputManifest.select(cutoff, SUBJECT, BarTimeframe.FIVE_MINUTES, List.of());
        var warming = FeatureInputManifest.select(cutoff, SUBJECT, BarTimeframe.FIVE_MINUTES,
                List.of(finalBar(0), finalBar(1)));
        var ready = FeatureInputManifest.select(cutoff, SUBJECT, BarTimeframe.FIVE_MINUTES,
                List.of(finalBar(0), finalBar(1), finalBar(2)));

        assertThat(FeatureReadinessEvaluator.assess(definition, unavailable).readiness())
                .isEqualTo(FeatureReadiness.UNAVAILABLE);
        assertThat(FeatureReadinessEvaluator.assess(definition, warming))
                .extracting(FeatureReadinessAssessment::readiness,
                        FeatureReadinessAssessment::availableBars,
                        FeatureReadinessAssessment::requiredBars)
                .containsExactly(FeatureReadiness.WARMING_UP, 2, 3);
        assertThat(FeatureReadinessEvaluator.assess(definition, ready).readiness())
                .isEqualTo(FeatureReadiness.READY);
    }

    @Test
    void goldenNumericPolicyRoundsOnlyThePublishedValue() {
        var definition = FeatureRegistryTest.definition("1.0.0");
        var manifest = FeatureInputManifest.select(
                cutoff("2026-08-03T04:00:00Z", "2026-08-03T04:00:02Z"),
                SUBJECT, BarTimeframe.FIVE_MINUTES,
                List.of(finalBar(0), finalBar(1), finalBar(2)));

        var value = FeatureValue.ready(definition, manifest, new BigDecimal("101.235"),
                at("2026-08-03T04:00:02Z"));

        assertThat(value.value()).isEqualByComparingTo("101.24");
        assertThat(value.unit()).isEqualTo(FeatureUnit.PRICE);
        assertThat(value.readiness()).isEqualTo(FeatureReadiness.READY);
        assertThat(value.inputManifestHash()).isEqualTo(manifest.contentHash());
    }

    @Test
    void futureCandidatesCannotChangeTheManifestOrFeatureResult() {
        var cutoff = cutoff("2026-08-03T04:00:00Z", "2026-08-03T04:00:02Z");
        var visible = List.of(finalBar(0), finalBar(1), finalBar(2));
        var withFuture = new ArrayList<>(visible);
        withFuture.add(finalBar(3));

        var baseline = FeatureInputManifest.select(cutoff, SUBJECT, BarTimeframe.FIVE_MINUTES, visible);
        var guarded = FeatureInputManifest.select(cutoff, SUBJECT, BarTimeframe.FIVE_MINUTES, withFuture);

        assertThat(guarded.bars()).isEqualTo(baseline.bars());
        assertThat(guarded.contentHash()).isEqualTo(baseline.contentHash());
        assertThat(FeatureValue.ready(FeatureRegistryTest.definition("1.0.0"), guarded,
                new BigDecimal("101.235"), at("2026-08-03T04:00:02Z")).value())
                .isEqualByComparingTo("101.24");
    }

    @Test
    void pointInTimeManifestUsesTheCorrectionOnlyAfterItWasKnown() {
        var original = finalBar(1);
        var correction = original.correct(values("101", "105", "100", "104", 2_000),
                original.observedThrough(), at("2026-08-03T04:10:00Z"),
                "Exchange correction", new ContentHash("d".repeat(64)));
        var candidates = List.of(finalBar(0), original, correction, finalBar(2));

        var before = FeatureInputManifest.select(
                cutoff("2026-08-03T04:30:00Z", "2026-08-03T04:09:59Z"),
                SUBJECT, BarTimeframe.FIVE_MINUTES, candidates);
        var after = FeatureInputManifest.select(
                cutoff("2026-08-03T04:30:00Z", "2026-08-03T04:10:00Z"),
                SUBJECT, BarTimeframe.FIVE_MINUTES, candidates);

        assertThat(before.bars().get(1)).isEqualTo(original);
        assertThat(after.bars().get(1)).isEqualTo(correction);
        assertThat(after.contentHash()).isNotEqualTo(before.contentHash());
    }

    @Test
    void rejectsPublicationBeforeTheDeclaredCausalDelay() {
        var manifest = FeatureInputManifest.select(
                cutoff("2026-08-03T04:00:00Z", "2026-08-03T04:00:02Z"),
                SUBJECT, BarTimeframe.FIVE_MINUTES,
                List.of(finalBar(0), finalBar(1), finalBar(2)));

        assertThatThrownBy(() -> FeatureValue.ready(FeatureRegistryTest.definition("1.0.0"),
                manifest, new BigDecimal("101.23"), at("2026-08-03T04:00:01Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("causal inputs or delay");
    }

    @Test
    void staleInputIsExplicitlyRejectedByReadiness() {
        var manifest = FeatureInputManifest.select(
                cutoff("2026-08-03T05:00:01Z", "2026-08-03T05:00:01Z"),
                SUBJECT, BarTimeframe.FIVE_MINUTES,
                List.of(finalBar(0), finalBar(1), finalBar(2)));

        assertThat(FeatureReadinessEvaluator.assess(FeatureRegistryTest.definition("1.0.0"), manifest)
                .readiness()).isEqualTo(FeatureReadiness.STALE);
    }

    @Test
    void sessionResetPolicyDoesNotCountPriorSessionWarmupBars() {
        var base = FeatureRegistryTest.definition("1.0.0");
        var resetDefinition = new FeatureDefinition(base.ref(), base.family(), base.formula(), base.outputUnit(),
                new BarInputRequirement(BarTimeframe.FIVE_MINUTES, 3,
                        base.inputRequirement().maximumStaleness(), true),
                new TreeMap<>(base.parameters()), base.numericPolicy(), base.initializationPolicy(),
                base.nullPolicy(), base.corporateActionPolicy(), base.causalDelay(), base.codeVersion());
        var manifest = FeatureInputManifest.select(
                cutoff("2026-08-03T04:00:00Z", "2026-08-03T04:00:02Z"), SUBJECT,
                BarTimeframe.FIVE_MINUTES,
                List.of(
                        finalBarAt("2026-08-02T03:45:00Z", LocalDate.of(2026, 8, 2), 0),
                        finalBarAt("2026-08-02T03:50:00Z", LocalDate.of(2026, 8, 2), 1),
                        finalBarAt("2026-08-03T03:45:00Z", LocalDate.of(2026, 8, 3), 2)));

        assertThat(FeatureReadinessEvaluator.assess(resetDefinition, manifest))
                .extracting(FeatureReadinessAssessment::readiness,
                        FeatureReadinessAssessment::availableBars)
                .containsExactly(FeatureReadiness.WARMING_UP, 1);
    }

    private static MarketBarRevision finalBar(int index) {
        var startsAt = at("2026-08-03T03:45:00Z").plusSeconds(index * 300L);
        return finalBarAt(startsAt.toString(), LocalDate.of(2026, 8, 3), index);
    }

    private static MarketBarRevision finalBarAt(String startsAtValue, LocalDate tradingDate, int index) {
        var startsAt = at(startsAtValue);
        var endsAt = startsAt.plusSeconds(300);
        var key = new MarketBarKey(SUBJECT,
                new MarketSessionId("NSE", tradingDate, "REGULAR"),
                BarTimeframe.FIVE_MINUTES, new BarInterval(startsAt, endsAt, false));
        return new MarketBarRevision(key, 2,
                values(Integer.toString(100 + index), Integer.toString(102 + index),
                        Integer.toString(99 + index), Integer.toString(101 + index), 1_000 + index),
                endsAt, BarFinalityState.FINAL, endsAt.plusSeconds(1), null,
                SOURCE_MANIFEST, "ohlcv-v1", "equity-intraday-v1");
    }

    private static MarketBarValues values(String open, String high, String low, String close, long volume) {
        return new MarketBarValues(new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), volume);
    }

    private static EvaluationCutoff cutoff(String analysis, String knowledge) {
        return new EvaluationCutoff(at(analysis), at(knowledge));
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
