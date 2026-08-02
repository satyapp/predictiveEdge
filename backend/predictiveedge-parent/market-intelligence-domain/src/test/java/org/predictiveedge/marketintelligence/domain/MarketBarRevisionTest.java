package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketBarRevisionTest {
    private static final ContentHash MANIFEST_ONE = new ContentHash("a".repeat(64));
    private static final ContentHash MANIFEST_TWO = new ContentHash("b".repeat(64));
    private static final BarFinalityPolicy FINALITY_POLICY =
            new BarFinalityPolicy(Duration.ofSeconds(30), "equity-intraday-v1");

    @Test
    void provisionalBarCannotFinalizeBeforeAllowedLatenessExpires() {
        var bar = provisionalBar();

        assertThatThrownBy(() -> bar.finalizeAt(
                FINALITY_POLICY, at("2026-08-03T03:50:29Z"), at("2026-08-03T03:50:29Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("watermark");
    }

    @Test
    void finalizationCreatesANewImmutableRevision() {
        var provisional = provisionalBar();
        var finalized = provisional.finalizeAt(
                FINALITY_POLICY, at("2026-08-03T03:50:30Z"), at("2026-08-03T03:50:31Z"));

        assertThat(provisional.finalityState()).isEqualTo(BarFinalityState.PROVISIONAL);
        assertThat(finalized.revision()).isEqualTo(2);
        assertThat(finalized.finalityState()).isEqualTo(BarFinalityState.FINAL);
        assertThat(finalized.key()).isEqualTo(provisional.key());
    }

    @Test
    void eligibilityRequiresBothAnalysisAndKnowledgeCutoffs() {
        var finalized = finalizedBar();

        assertThat(finalized.isEligible(new EvaluationCutoff(
                at("2026-08-03T03:49:59Z"), at("2026-08-03T03:51:00Z")))).isFalse();
        assertThat(finalized.isEligible(new EvaluationCutoff(
                at("2026-08-03T03:50:00Z"), at("2026-08-03T03:50:30Z")))).isFalse();
        assertThat(finalized.isEligible(new EvaluationCutoff(
                at("2026-08-03T03:50:00Z"), at("2026-08-03T03:50:31Z")))).isTrue();
    }

    @Test
    void correctionPreservesIdentityAndAddsAnAuditableRevision() {
        var finalized = finalizedBar();
        var correctedValues = values("100", "104", "99", "103", 1_250);

        var corrected = finalized.correct(correctedValues, at("2026-08-03T03:50:00Z"),
                at("2026-08-03T04:30:00Z"), "Exchange corrected late trade", MANIFEST_TWO);

        assertThat(corrected.key()).isEqualTo(finalized.key());
        assertThat(corrected.revision()).isEqualTo(3);
        assertThat(corrected.finalityState()).isEqualTo(BarFinalityState.CORRECTED);
        assertThat(corrected.correctionReason()).isEqualTo("Exchange corrected late trade");
        assertThat(corrected.inputManifestHash()).isEqualTo(MANIFEST_TWO);
    }

    @Test
    void pointInTimeSelectionCannotSeeAFutureCorrection() {
        var finalized = finalizedBar();
        var corrected = finalized.correct(values("100", "104", "99", "103", 1_250),
                at("2026-08-03T03:50:00Z"), at("2026-08-03T04:30:00Z"),
                "Exchange corrected late trade", MANIFEST_TWO);

        assertThat(PointInTimeMarketBarSelector.selectLatest(List.of(finalized, corrected), key(),
                new EvaluationCutoff(at("2026-08-03T05:00:00Z"), at("2026-08-03T04:29:59Z"))))
                .contains(finalized);
        assertThat(PointInTimeMarketBarSelector.selectLatest(List.of(finalized, corrected), key(),
                new EvaluationCutoff(at("2026-08-03T05:00:00Z"), at("2026-08-03T04:30:00Z"))))
                .contains(corrected);
    }

    @Test
    void correctionRequiresAReasonAndCannotMutateAProvisionalBar() {
        assertThatThrownBy(() -> provisionalBar().correct(values("100", "103", "99", "102", 1_100),
                at("2026-08-03T03:50:00Z"), at("2026-08-03T04:00:00Z"), "late trade", MANIFEST_TWO))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> finalizedBar().correct(values("100", "103", "99", "102", 1_100),
                at("2026-08-03T03:50:00Z"), at("2026-08-03T04:00:00Z"), " ", MANIFEST_TWO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correction reason");
    }

    @Test
    void rejectsInvalidOhlcAndVolume() {
        assertThatThrownBy(() -> values("100", "99", "98", "101", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OHLC");
        assertThatThrownBy(() -> values("100", "101", "99", "100", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Volume");
    }

    private static MarketBarRevision provisionalBar() {
        return MarketBarRevision.provisional(key(), values("100", "102", "99", "101", 1_000),
                at("2026-08-03T03:50:00Z"), at("2026-08-03T03:50:02Z"), MANIFEST_ONE,
                "ohlcv-v1", FINALITY_POLICY.version());
    }

    private static MarketBarRevision finalizedBar() {
        return provisionalBar().finalizeAt(
                FINALITY_POLICY, at("2026-08-03T03:50:30Z"), at("2026-08-03T03:50:31Z"));
    }

    private static MarketBarKey key() {
        return new MarketBarKey(new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:RELIANCE"),
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 3), "REGULAR"),
                BarTimeframe.FIVE_MINUTES,
                new BarInterval(at("2026-08-03T03:45:00Z"), at("2026-08-03T03:50:00Z"), false));
    }

    private static MarketBarValues values(String open, String high, String low, String close, long volume) {
        return new MarketBarValues(new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), volume);
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
