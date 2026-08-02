package org.predictiveedge.platform.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class OutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-31T05:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration RETRY = Duration.ofSeconds(10);

    @Test
    void publishesClaimedEventsAndRetainsBrokerCoordinates() {
        OutboxEntry first = entry(1);
        OutboxEntry second = entry(2);
        RecordingStore store = new RecordingStore(List.of(first, second));
        List<UUID> transported = new ArrayList<>();
        EventTransport transport = publication -> {
            transported.add(publication.event().metadata().eventId());
            return new PublicationReceipt("pe.test.v1", 0, transported.size() - 1L, NOW);
        };

        DispatchSummary result = dispatcher(store, transport).dispatch(10);

        assertThat(result).isEqualTo(new DispatchSummary(2, 2, 0));
        assertThat(transported).containsExactly(
                first.publication().event().metadata().eventId(),
                second.publication().event().metadata().eventId());
        assertThat(store.published).containsOnlyKeys(first.outboxId(), second.outboxId());
        assertThat(store.failures).isEmpty();
        assertThat(store.claimedAt).isEqualTo(NOW);
        assertThat(store.leaseDuration).isEqualTo(LEASE);
        assertThat(store.batchSize).isEqualTo(10);
    }

    @Test
    void recordsRedactedFailureAndContinuesTheBatch() {
        OutboxEntry failed = entry(1);
        OutboxEntry successful = entry(2);
        RecordingStore store = new RecordingStore(List.of(failed, successful));
        EventTransport transport = publication -> {
            if (publication.event().metadata().eventId().equals(
                    failed.publication().event().metadata().eventId())) {
                throw new IllegalStateException("secret-token-must-not-be-persisted");
            }
            return new PublicationReceipt("pe.test.v1", 1, 42, NOW);
        };

        DispatchSummary result = dispatcher(store, transport).dispatch(2);

        assertThat(result).isEqualTo(new DispatchSummary(2, 1, 1));
        assertThat(store.published).containsOnlyKeys(successful.outboxId());
        assertThat(store.failures).containsOnlyKeys(failed.outboxId());
        PublicationFailure failure = store.failures.get(failed.outboxId());
        assertThat(failure.errorType()).isEqualTo("IllegalStateException");
        assertThat(failure.toString()).doesNotContain("secret-token");
        assertThat(failure.failedAt()).isEqualTo(NOW);
        assertThat(failure.retryAt()).isEqualTo(NOW.plus(RETRY));
    }

    @Test
    void rejectsInvalidDispatchConfigurationAndBatchSize() {
        RecordingStore store = new RecordingStore(List.of());
        EventTransport transport = publication -> new PublicationReceipt("pe.test.v1", 0, 0, NOW);

        assertThatThrownBy(() -> new OutboxDispatcher(
                store, transport, Clock.systemUTC(), Duration.ZERO, RETRY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lease duration");
        assertThatThrownBy(() -> dispatcher(store, transport).dispatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size");
    }

    private static OutboxDispatcher dispatcher(OutboxStore store, EventTransport transport) {
        return new OutboxDispatcher(
                store, transport, Clock.fixed(NOW, ZoneOffset.UTC), LEASE, RETRY);
    }

    private static OutboxEntry entry(long version) {
        UUID eventId = new UUID(0, version);
        EventMetadata metadata = new EventMetadata(
                eventId,
                "TestEvent.Created",
                new SchemaVersion(1, 0),
                "eventing-test",
                "TestAggregate",
                "aggregate-1",
                version,
                "aggregate-1",
                NOW,
                NOW,
                NOW,
                null,
                NOW,
                NOW,
                eventId,
                eventId,
                null,
                null,
                null,
                null,
                List.of(),
                null);
        EventEnvelope envelope = EventEnvelope.create(
                metadata,
                JsonNodeFactory.instance.objectNode().put("version", version),
                DataClassification.INTERNAL);
        return new OutboxEntry(
                new UUID(1, version), new UUID(2, version),
                new EventPublication("pe.test.v1", envelope), 0);
    }

    private static final class RecordingStore implements OutboxStore {
        private final List<OutboxEntry> entries;
        private final Map<UUID, PublicationReceipt> published = new LinkedHashMap<>();
        private final Map<UUID, PublicationFailure> failures = new LinkedHashMap<>();
        private int batchSize;
        private Instant claimedAt;
        private Duration leaseDuration;

        private RecordingStore(List<OutboxEntry> entries) {
            this.entries = entries;
        }

        @Override
        public List<OutboxEntry> claimPending(int requestedBatchSize, Instant requestedAt, Duration requestedLease) {
            batchSize = requestedBatchSize;
            claimedAt = requestedAt;
            leaseDuration = requestedLease;
            return entries;
        }

        @Override
        public void markPublished(UUID outboxId, UUID leaseId, PublicationReceipt receipt) {
            published.put(outboxId, receipt);
        }

        @Override
        public void markFailed(UUID outboxId, UUID leaseId, PublicationFailure failure) {
            failures.put(outboxId, failure);
        }
    }
}
