package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DeterministicMarketContextTest {
    private static final ObservationSubject SUBJECT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE");
    private static final EvaluationCutoff CUTOFF = new EvaluationCutoff(
            at("2026-08-03T04:15:00Z"), at("2026-08-03T04:15:02Z"));
    private static final Instant READY_AT = at("2026-08-03T04:15:02Z");
    private static final Instant EXPIRES_AT = at("2026-08-03T04:20:00Z");

    @Test
    void governedCalculatorsMatchGoldenVectors() {
        var features = calculateFeatures(finalBars());

        assertThat(features.get(0).value()).isEqualByComparingTo("104.5000");
        assertThat(features.get(1).value()).isEqualByComparingTo("104.0000");
        assertThat(features.get(2).value()).isEqualByComparingTo("2.0000");
        assertThat(features.get(3).value()).isEqualByComparingTo("2.0000");
    }

    @Test
    void capturedSessionProducesIdenticalLiveAndReplaySemanticHashes() {
        var liveCandidates = finalBars();
        var replayCandidates = new ArrayList<>(liveCandidates);
        java.util.Collections.reverse(replayCandidates);
        replayCandidates.add(finalBar(6, 106, 1_500)); // Ends after the fixed analysis cutoff.

        var live = compose(liveCandidates, passingQuality());
        var replay = compose(replayCandidates, passingQuality());

        assertThat(live.regime()).isEqualTo(MarketRegime.BULLISH_TREND);
        assertThat(live.dimensions().get(EvidenceDimension.VOLATILITY).state()).isEqualTo(EvidenceState.NORMAL);
        assertThat(live.dimensions().get(EvidenceDimension.PARTICIPATION).state()).isEqualTo(EvidenceState.STRONG);
        assertThat(replay.inputLineageHash()).isEqualTo(live.inputLineageHash());
        assertThat(replay.semanticHash()).isEqualTo(live.semanticHash());
    }

    @Test
    void correlatedEvidenceFamilyIsCollapsedBeforeConfidence() {
        var feature = calculateFeatures(finalBars()).getFirst();
        var weaker = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BULLISH, 60, 5,
                "DIRECTION:EMA", feature.valueTime(), feature.availableAt(), EXPIRES_AT, List.of(feature), "rule-v1");
        var stronger = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BULLISH, 80, 5,
                "DIRECTION:EMA", feature.valueTime(), feature.availableAt(), EXPIRES_AT, List.of(feature), "rule-v1");

        var fused = EvidenceFusion.fuse(List.of(weaker, stronger), passingQuality(), new FusionPolicy("fusion-v1", 10));

        assertThat(fused.get(EvidenceDimension.DIRECTION).confidence()).isEqualTo(75);
        assertThat(fused.get(EvidenceDimension.DIRECTION).selectedEvidence()).hasSize(1);
    }

    @Test
    void independentBalancedOppositionRemainsExplicitlyMixed() {
        var feature = calculateFeatures(finalBars()).getFirst();
        var bullish = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BULLISH, 75, 5,
                "DIRECTION:EMA", feature.valueTime(), feature.availableAt(), EXPIRES_AT, List.of(feature), "rule-v1");
        var bearish = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BEARISH, 72, 5,
                "DIRECTION:LEADERSHIP", feature.valueTime(), feature.availableAt(), EXPIRES_AT,
                List.of(feature), "rule-v1");

        var direction = EvidenceFusion.fuse(List.of(bullish, bearish), passingQuality(),
                new FusionPolicy("fusion-v1", 10)).get(EvidenceDimension.DIRECTION);

        assertThat(direction.state()).isEqualTo(EvidenceState.MIXED);
        assertThat(direction.conflict()).isTrue();
    }

    @Test
    void contradictoryCorrelatedClaimsRemainVisibleWithoutMultiplyingConfidence() {
        var feature = calculateFeatures(finalBars()).getFirst();
        var bullish = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BULLISH, 80, 5,
                "DIRECTION:EMA", feature.valueTime(), feature.availableAt(), EXPIRES_AT, List.of(feature), "rule-v1");
        var bearish = MarketEvidence.create(EvidenceDimension.DIRECTION, EvidenceState.BEARISH, 70, 5,
                "DIRECTION:EMA", feature.valueTime(), feature.availableAt(), EXPIRES_AT, List.of(feature), "rule-v2");

        var direction = EvidenceFusion.fuse(List.of(bullish, bearish), passingQuality(),
                new FusionPolicy("fusion-v1", 10)).get(EvidenceDimension.DIRECTION);

        assertThat(direction.state()).isEqualTo(EvidenceState.MIXED);
        assertThat(direction.conflict()).isTrue();
        assertThat(direction.confidence()).isEqualTo(75);
        assertThat(direction.selectedEvidence()).hasSize(2);
    }

    @Test
    void degradedQualityAppliesAMandatoryContextConfidenceCap() {
        var degraded = QualityEngine.assess(CUTOFF, QualityPolicyTest.policy(),
                List.of(new QualityFinding(QualityIssueCode.STALE_FALLBACK, "INDIA_VIX",
                        "Fallback is stale", List.of())), List.of());

        var context = compose(finalBars(), degraded);

        assertThat(degraded.confidenceCap()).isEqualTo(40);
        assertThat(context.confidence()).isLessThanOrEqualTo(40);
        assertThat(context.dimensions().values())
                .allSatisfy(dimension -> assertThat(dimension.confidence()).isLessThanOrEqualTo(40));
    }

    @Test
    void blockingQualitySuspendsContextRegardlessOfHealthyEvidence() {
        var blocked = QualityEngine.assess(CUTOFF, QualityPolicyTest.policy(),
                List.of(new QualityFinding(QualityIssueCode.INVALID_SESSION, "NSE:REGULAR",
                        "Session identity could not be validated", List.of())), List.of());

        var context = compose(finalBars(), blocked);

        assertThat(context.regime()).isEqualTo(MarketRegime.SUSPENDED);
        assertThat(context.confidence()).isZero();
    }

    private static MarketContextSnapshot compose(List<MarketBarRevision> candidates, QualityAssessment quality) {
        var features = calculateFeatures(candidates);
        var policy = new InitialEvidencePolicy("initial-evidence-v1", new BigDecimal("1.0"),
                new BigDecimal("3.0"), new BigDecimal("0.75"), new BigDecimal("1.50"), 5);
        var evidence = List.of(
                InitialEvidenceFactory.direction(features.get(0), features.get(1), EXPIRES_AT, policy),
                InitialEvidenceFactory.volatility(features.get(2), new BigDecimal("105"), EXPIRES_AT, policy),
                InitialEvidenceFactory.participation(features.get(3), EXPIRES_AT, policy));
        return MarketContextComposer.compose(new MarketContextKey(ContextScopeType.INSTRUMENT,
                        "NSE:RELIANCE", "INTRADAY"), CUTOFF, READY_AT, EXPIRES_AT, features, evidence, quality,
                new FusionPolicy("fusion-v1", 10), "context-v1");
    }

    private static List<FeatureValue> calculateFeatures(List<MarketBarRevision> candidates) {
        var manifest = FeatureInputManifest.select(CUTOFF, SUBJECT, BarTimeframe.FIVE_MINUTES, candidates);
        var fast = definition("EMA_FAST", FeatureFamily.DIRECTION, FeatureUnit.PRICE, 2, "period", "2");
        var slow = definition("EMA_SLOW", FeatureFamily.DIRECTION, FeatureUnit.PRICE, 3, "period", "3");
        var atr = definition("ATR_2", FeatureFamily.RISK_DISTANCE, FeatureUnit.PRICE, 3, "period", "2");
        var volume = definition("ROLLING_RVOL", FeatureFamily.PARTICIPATION, FeatureUnit.RATIO,
                4, "baselineBars", "3");
        var engine = new FeatureCalculationEngine(List.of(
                new ExponentialMovingAverageCalculator(fast.ref(), 2),
                new ExponentialMovingAverageCalculator(slow.ref(), 3),
                new AverageTrueRangeCalculator(atr.ref(), 2),
                new RollingRelativeVolumeCalculator(volume.ref(), 3)));
        return List.of(engine.calculate(fast, manifest, READY_AT), engine.calculate(slow, manifest, READY_AT),
                engine.calculate(atr, manifest, READY_AT), engine.calculate(volume, manifest, READY_AT));
    }

    private static FeatureDefinition definition(String id, FeatureFamily family, FeatureUnit unit,
            int requiredBars, String parameter, String value) {
        var parameters = new TreeMap<String, String>(); parameters.put(parameter, value);
        return new FeatureDefinition(new FeatureDefinitionRef(new FeatureId(id), "1.0.0"), family, id,
                unit, new BarInputRequirement(BarTimeframe.FIVE_MINUTES, requiredBars, Duration.ofHours(1), false),
                parameters, new NumericPolicy(4, RoundingMode.HALF_UP, RoundingBoundary.FINAL_OUTPUT,
                        new BigDecimal("0.000001"), "decimal-v1"), "governed seed", "unavailable",
                "split adjusted", Duration.ZERO, "kernel-v1");
    }

    private static QualityAssessment passingQuality() {
        return QualityEngine.assess(CUTOFF, QualityPolicyTest.policy(), List.of(),
                List.of(new CoverageMeasurement("CAPTURED-SESSION", 6, 6, List.of())));
    }

    private static List<MarketBarRevision> finalBars() {
        return List.of(finalBar(0, 100, 1_000), finalBar(1, 101, 1_100), finalBar(2, 102, 1_200),
                finalBar(3, 103, 1_300), finalBar(4, 104, 1_400), finalBar(5, 105, 2_600));
    }

    private static MarketBarRevision finalBar(int index, int close, long volume) {
        var start = at("2026-08-03T03:45:00Z").plusSeconds(index * 300L); var end = start.plusSeconds(300);
        var values = new MarketBarValues(BigDecimal.valueOf(close), BigDecimal.valueOf(close + 1L),
                BigDecimal.valueOf(close - 1L), BigDecimal.valueOf(close), volume);
        return new MarketBarRevision(new MarketBarKey(SUBJECT,
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 3), "REGULAR"), BarTimeframe.FIVE_MINUTES,
                new BarInterval(start, end, false)), 2, values, end, BarFinalityState.FINAL, end.plusSeconds(1),
                null, new ContentHash("e".repeat(64)), "ohlcv-v1", "finality-v1");
    }

    private static Instant at(String value) { return Instant.parse(value); }
}
