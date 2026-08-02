package org.predictiveedge.platform.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class IdempotentEventConsumerTest {
    private static final Instant NOW = Instant.parse("2026-07-31T06:00:00Z");

    @Test
    void delegatesHandlingToTheAtomicInboxBoundary() {
        AtomicInteger handled = new AtomicInteger();
        InboxTransaction inbox = (consumer, delivery, handler) -> {
            handler.handle(delivery.event());
            return ProcessingResult.PROCESSED;
        };

        ProcessingResult result = new IdempotentEventConsumer(inbox).consume(
                "guardian-v1", delivery(), event -> handled.incrementAndGet());

        assertThat(result).isEqualTo(ProcessingResult.PROCESSED);
        assertThat(handled).hasValue(1);
    }

    @Test
    void preservesDuplicateOutcomeWithoutCallingTheBusinessHandler() {
        AtomicInteger handled = new AtomicInteger();
        InboxTransaction inbox = (consumer, delivery, handler) -> ProcessingResult.DUPLICATE;

        ProcessingResult result = new IdempotentEventConsumer(inbox).consume(
                "guardian-v1", delivery(), event -> handled.incrementAndGet());

        assertThat(result).isEqualTo(ProcessingResult.DUPLICATE);
        assertThat(handled).hasValue(0);
    }

    @Test
    void rejectsAnUnnamedConsumer() {
        IdempotentEventConsumer consumer = new IdempotentEventConsumer(
                (name, delivery, handler) -> ProcessingResult.PROCESSED);

        assertThatThrownBy(() -> consumer.consume(" ", delivery(), event -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Consumer name");
    }

    private static EventDelivery delivery() {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        EventMetadata metadata = new EventMetadata(
                eventId, "Trade.Registered", new SchemaVersion(1, 0), "trade-registration",
                "Trade", "trade-1", 1, "trade-1", NOW, NOW, NOW, NOW, NOW, NOW,
                eventId, eventId, null, "recommendation-1", "trade-1", "account-1",
                List.of(), null);
        EventEnvelope envelope = EventEnvelope.create(
                metadata, JsonNodeFactory.instance.objectNode().put("tradeId", "trade-1"),
                DataClassification.CONFIDENTIAL);
        return new EventDelivery(envelope, "pe.trades.v1", 0, 10, NOW);
    }
}
