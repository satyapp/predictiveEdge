package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CanonicalObservationRevisionTest {
    @Test
    void normalizesProviderNeutralIdentityAndHash() {
        CanonicalObservationRevision observation = observation(
                Instant.parse("2026-08-03T03:50:01Z"),
                Instant.parse("2026-08-03T03:50:02Z"));

        assertThat(observation.sourceId()).isEqualTo("NSE_PRIMARY");
        assertThat(observation.subject().id()).isEqualTo("NSE:RELIANCE");
        assertThat(observation.descriptor().kind()).isEqualTo(ObservationKind.BAR);
        assertThat(observation.descriptor().schemaId().value()).isEqualTo("market.bar.v1");
        assertThat(observation.rawPayloadHash().value()).isEqualTo("a".repeat(64));
    }

    @Test
    void rejectsAnObservationUsableBeforePlatformReceipt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> observation(
                        Instant.parse("2026-08-03T03:50:03Z"),
                        Instant.parse("2026-08-03T03:50:02Z")))
                .withMessageContaining("Usable time cannot precede received time");
    }

    @Test
    void rejectsSourcePublicationReportedAfterPlatformReceipt() {
        Instant receivedAt = Instant.parse("2026-08-03T03:50:01Z");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CanonicalObservationRevision(
                        UUID.randomUUID(),
                        1,
                        new ObservationDescriptor(
                                ObservationKind.CORPORATE_ANNOUNCEMENT,
                                new ObservationSchemaId("equity.corporate-announcement.v1")),
                        new ObservationSubject(ObservationSubjectType.ISSUER, "NSE:RELIANCE"),
                        "nse_primary",
                        "announcement-1",
                        Instant.parse("2026-08-03T03:50:00Z"),
                        receivedAt.plusMillis(1),
                        receivedAt,
                        receivedAt.plusMillis(2),
                        new ContentHash("b".repeat(64))))
                .withMessageContaining("publication time cannot follow");
    }

    private static CanonicalObservationRevision observation(Instant receivedAt, Instant usableAt) {
        return new CanonicalObservationRevision(
                UUID.randomUUID(),
                1,
                new ObservationDescriptor(ObservationKind.BAR, new ObservationSchemaId("MARKET.BAR.V1")),
                new ObservationSubject(ObservationSubjectType.INSTRUMENT, "nse:reliance"),
                "nse_primary",
                "source-event-1",
                Instant.parse("2026-08-03T03:50:00Z"),
                receivedAt.minusMillis(1),
                receivedAt,
                usableAt,
                new ContentHash("A".repeat(64)));
    }
}
