package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PointInTimeObservationSelectorTest {
    private static final Instant EVENT_TIME = Instant.parse("2026-08-03T03:50:00Z");
    private static final EvaluationCutoff CUTOFF = new EvaluationCutoff(
            Instant.parse("2026-08-03T03:55:00Z"),
            Instant.parse("2026-08-03T03:55:01Z"));
    private static final UUID RELIANCE_OBSERVATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NIFTY_OBSERVATION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void excludesAnOccurredFactUntilItBecameUsable() {
        CanonicalObservationRevision delayed = observation(
                RELIANCE_OBSERVATION_ID,
                1,
                "NSE:RELIANCE",
                EVENT_TIME,
                CUTOFF.knowledgeCutoff().plusMillis(1),
                'a');

        PointInTimeInputManifest manifest = PointInTimeObservationSelector.select(List.of(delayed), CUTOFF);

        assertThat(manifest.observations()).isEmpty();
    }

    @Test
    void selectsTheLatestRevisionKnownAtTheKnowledgeCutoff() {
        CanonicalObservationRevision original = observation(
                RELIANCE_OBSERVATION_ID, 1, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(1), 'a');
        CanonicalObservationRevision correction = observation(
                RELIANCE_OBSERVATION_ID, 2, "NSE:RELIANCE", EVENT_TIME,
                CUTOFF.knowledgeCutoff().plusSeconds(30), 'b');

        PointInTimeInputManifest asKnown = PointInTimeObservationSelector.select(
                List.of(correction, original), CUTOFF);
        PointInTimeInputManifest afterCorrection = PointInTimeObservationSelector.select(
                List.of(correction, original),
                new EvaluationCutoff(CUTOFF.analysisCutoff(), correction.usableAt()));

        assertThat(asKnown.observations()).containsExactly(original);
        assertThat(afterCorrection.observations()).containsExactly(correction);
    }

    @Test
    void appendingFutureEventsAndCorrectionsCannotChangeAnEarlierManifest() {
        CanonicalObservationRevision original = observation(
                RELIANCE_OBSERVATION_ID, 1, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(1), 'a');
        PointInTimeInputManifest beforeAppend = PointInTimeObservationSelector.select(
                List.of(original), CUTOFF);

        CanonicalObservationRevision laterCorrection = observation(
                RELIANCE_OBSERVATION_ID, 2, "NSE:RELIANCE", EVENT_TIME,
                CUTOFF.knowledgeCutoff().plusSeconds(30), 'b');
        CanonicalObservationRevision futureEvent = observation(
                NIFTY_OBSERVATION_ID, 1, "NSE:NIFTY50",
                CUTOFF.analysisCutoff().plusSeconds(1),
                CUTOFF.knowledgeCutoff().plusSeconds(1), 'c');
        PointInTimeInputManifest afterAppend = PointInTimeObservationSelector.select(
                List.of(futureEvent, laterCorrection, original), CUTOFF);

        assertThat(afterAppend.observations()).isEqualTo(beforeAppend.observations());
        assertThat(afterAppend.contentHash()).isEqualTo(beforeAppend.contentHash());
    }

    @Test
    void manifestOrderingAndHashDoNotDependOnArrivalCollectionOrder() {
        CanonicalObservationRevision reliance = observation(
                RELIANCE_OBSERVATION_ID, 1, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(2), 'a');
        CanonicalObservationRevision nifty = observation(
                NIFTY_OBSERVATION_ID, 1, "NSE:NIFTY50", EVENT_TIME.minusSeconds(1),
                EVENT_TIME.plusSeconds(1), 'b');

        PointInTimeInputManifest first = PointInTimeObservationSelector.select(
                List.of(reliance, nifty), CUTOFF);
        PointInTimeInputManifest second = PointInTimeObservationSelector.select(
                List.of(nifty, reliance), CUTOFF);

        assertThat(first.observations()).containsExactly(nifty, reliance);
        assertThat(second).isEqualTo(first);
        assertThat(first.contentHash().value()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsARevisionThatChangesCanonicalObservationIdentity() {
        CanonicalObservationRevision original = observation(
                RELIANCE_OBSERVATION_ID, 1, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(1), 'a');
        CanonicalObservationRevision invalidCorrection = observation(
                RELIANCE_OBSERVATION_ID, 2, "NSE:INFY", EVENT_TIME,
                EVENT_TIME.plusSeconds(2), 'b');

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PointInTimeObservationSelector.select(
                        List.of(original, invalidCorrection), CUTOFF))
                .withMessageContaining("preserve canonical identity");
    }

    @Test
    void rejectsARevisionTimelineThatMovesBackwardInKnowledgeTime() {
        CanonicalObservationRevision original = observation(
                RELIANCE_OBSERVATION_ID, 1, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(2), 'a');
        CanonicalObservationRevision invalidCorrection = observation(
                RELIANCE_OBSERVATION_ID, 2, "NSE:RELIANCE", EVENT_TIME,
                EVENT_TIME.plusSeconds(1), 'b');

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PointInTimeObservationSelector.select(
                        List.of(original, invalidCorrection), CUTOFF))
                .withMessageContaining("revision order");
    }

    @Test
    void rejectsAContentHashThatDoesNotMatchTheManifest() {
        PointInTimeInputManifest manifest = PointInTimeObservationSelector.select(List.of(), CUTOFF);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PointInTimeInputManifest(
                        CUTOFF, manifest.observations(), new ContentHash("f".repeat(64))))
                .withMessageContaining("does not match");
    }

    private static CanonicalObservationRevision observation(
            UUID observationId,
            int revision,
            String subjectId,
            Instant eventTime,
            Instant usableAt,
            char hashCharacter) {
        return new CanonicalObservationRevision(
                observationId,
                revision,
                new ObservationDescriptor(ObservationKind.BAR, new ObservationSchemaId("market.bar.v1")),
                new ObservationSubject(
                        subjectId.contains("NIFTY")
                                ? ObservationSubjectType.INDEX
                                : ObservationSubjectType.INSTRUMENT,
                        subjectId),
                "nse_primary",
                observationId.toString(),
                eventTime,
                usableAt.minusMillis(2),
                usableAt.minusMillis(1),
                usableAt,
                new ContentHash(String.valueOf(hashCharacter).repeat(64)));
    }
}
