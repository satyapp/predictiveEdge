package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ExpandedEvidenceFactoryTest {
    private static final ObservationSubject SUBJECT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE");
    private static final Instant VALUE_TIME = Instant.parse("2026-08-03T04:15:00Z");
    private static final Instant AVAILABLE_AT = VALUE_TIME.plusSeconds(1);
    private static final Instant EXPIRES_AT = VALUE_TIME.plusSeconds(300);
    private static final EvaluationCutoff CUTOFF = new EvaluationCutoff(VALUE_TIME, AVAILABLE_AT);
    private static final ContentHash CONTEXT_HASH = new ContentHash("a".repeat(64));
    private static final ExpandedEvidencePolicy POLICY = new ExpandedEvidencePolicy("expanded-v1",
            decimal("20"), decimal("1"), decimal("3"), decimal("0.5"), decimal("45"), decimal("55"),
            decimal("1"), decimal("2"), decimal("0.5"), decimal("-20"), decimal("20"), 5);

    @Test
    void mapsCompleteEquityProfileToIndependentTypedEvidence() {
        var evidence = new ArrayList<MarketEvidence>();
        evidence.add(InitialEvidenceFactory.direction(feature("EMA_20", "105"), feature("EMA_50", "100"),
                EXPIRES_AT, initialPolicy()));
        evidence.add(ExpandedEvidenceFactory.trendQuality(feature("PLUS_DI_14", "30"),
                feature("MINUS_DI_14", "20"), feature("ADX_14", "25"), EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.volatility(feature("BB_WIDTH_20_2", "2"), EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.location(feature("SESSION_VWAP", "100"), decimal("101"),
                EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.trigger(feature("DONCHIAN_UPPER_20", "105"),
                feature("DONCHIAN_LOWER_20", "95"), decimal("106"), EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.momentum(feature("RSI_14", "65"), EXPIRES_AT, POLICY));
        evidence.add(InitialEvidenceFactory.participation(feature("TIME_ADJUSTED_RVOL", "2"),
                EXPIRES_AT, initialPolicy()));
        evidence.add(ExpandedEvidenceFactory.riskDistance(feature("ATR_14", "3"), decimal("100"),
                EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.breadth(breadth(), EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.marketStress(vix(), EXPIRES_AT, POLICY));
        evidence.add(ExpandedEvidenceFactory.relativeLeadership(feature("RELATIVE_LEADERSHIP_20", "2"),
                EXPIRES_AT, POLICY));

        var dimensions = EvidenceFusion.fuse(evidence, passingQuality(), new FusionPolicy("fusion-v1", 10));

        assertThat(dimensions).hasSize(EvidenceDimension.values().length);
        assertThat(dimensions).allSatisfy((dimension, assessment) -> {
            assertThat(assessment.state()).as(dimension.name()).isNotEqualTo(EvidenceState.UNKNOWN);
            assertThat(assessment.selectedEvidence()).as(dimension.name()).hasSize(1);
        });
        assertThat(dimensions.get(EvidenceDimension.DIRECTION).state()).isEqualTo(EvidenceState.BULLISH);
        assertThat(dimensions.get(EvidenceDimension.TREND_QUALITY).state()).isEqualTo(EvidenceState.BULLISH);
        assertThat(dimensions.get(EvidenceDimension.VOLATILITY).state()).isEqualTo(EvidenceState.NORMAL);
        assertThat(dimensions.get(EvidenceDimension.INTRADAY_LOCATION).state()).isEqualTo(EvidenceState.ABOVE_VALUE);
        assertThat(dimensions.get(EvidenceDimension.TRIGGER).state()).isEqualTo(EvidenceState.BREAKOUT);
        assertThat(dimensions.get(EvidenceDimension.MOMENTUM).state()).isEqualTo(EvidenceState.BULLISH);
        assertThat(dimensions.get(EvidenceDimension.PARTICIPATION).state()).isEqualTo(EvidenceState.STRONG);
        assertThat(dimensions.get(EvidenceDimension.RISK_DISTANCE).state()).isEqualTo(EvidenceState.HIGH);
        assertThat(dimensions.get(EvidenceDimension.BREADTH).state()).isEqualTo(EvidenceState.BULLISH);
        assertThat(dimensions.get(EvidenceDimension.MARKET_STRESS).state()).isEqualTo(EvidenceState.ELEVATED);
        assertThat(dimensions.get(EvidenceDimension.RELATIVE_LEADERSHIP).state()).isEqualTo(EvidenceState.BULLISH);
    }

    @Test
    void contextualEvidenceCarriesSourceManifestWithoutInventingAFeature() {
        var evidence = ExpandedEvidenceFactory.breadth(breadth(), EXPIRES_AT, POLICY);

        assertThat(evidence.sourceFeatures()).isEmpty();
        assertThat(evidence.contextualInputHashes()).containsExactly(CONTEXT_HASH);
        assertThat(evidence.effectiveAt()).isEqualTo(CUTOFF.analysisCutoff());
        assertThat(evidence.detectedAt()).isEqualTo(CUTOFF.knowledgeCutoff());
    }

    @Test
    void rejectsUnsynchronizedOrInvalidFeatureInputs() {
        var late = feature("MINUS_DI_14", "20", VALUE_TIME.plusSeconds(300));

        assertThatThrownBy(() -> ExpandedEvidenceFactory.trendQuality(feature("PLUS_DI_14", "30"), late,
                feature("ADX_14", "25"), EXPIRES_AT.plusSeconds(300), POLICY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must share");
        assertThatThrownBy(() -> ExpandedEvidenceFactory.riskDistance(feature("ATR_14", "3"), BigDecimal.ZERO,
                EXPIRES_AT, POLICY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid");
    }

    @Test
    void rejectsOutOfDomainPolicyThresholds() {
        assertThatThrownBy(() -> new ExpandedEvidencePolicy("invalid", decimal("20"), decimal("1"), decimal("3"),
                decimal("0.5"), decimal("-1"), decimal("55"), decimal("1"), decimal("2"), decimal("0.5"),
                decimal("-20"), decimal("20"), 5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static FeatureValue feature(String id, String value) {
        return feature(id, value, VALUE_TIME);
    }

    private static FeatureValue feature(String id, String value, Instant valueTime) {
        return new FeatureValue(new FeatureDefinitionRef(new FeatureId(id), "1.0.0"), SUBJECT,
                BarTimeframe.FIVE_MINUTES, decimal(value), FeatureUnit.RATIO, valueTime,
                valueTime.minusSeconds(300), valueTime, valueTime.plusSeconds(1), BarFinalityState.FINAL,
                FeatureReadiness.READY, new TreeMap<>(), "kernel-v1", new ContentHash("b".repeat(64)));
    }

    private static InitialEvidencePolicy initialPolicy() {
        return new InitialEvidencePolicy("initial-v1", decimal("1"), decimal("3"), decimal("0.75"),
                decimal("1.5"), 5);
    }

    private static AdvanceDeclineSnapshot breadth() {
        return new AdvanceDeclineSnapshot("NSE-MVP", "universe-v1", CUTOFF, 3, 1, 0,
                new CoverageMeasurement("NSE-MVP", 4, 4, List.of()), decimal("50"), CONTEXT_HASH);
    }

    private static VolatilityIndexContext vix() {
        return new VolatilityIndexContext(decimal("22"), decimal("10"), MarketStressState.ELEVATED,
                VALUE_TIME, AVAILABLE_AT, "vix-v1", new ContentHash("c".repeat(64)));
    }

    private static QualityAssessment passingQuality() {
        return QualityEngine.assess(CUTOFF, QualityPolicyTest.policy(), List.of(),
                List.of(new CoverageMeasurement("NSE-MVP", 4, 4, List.of())));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
