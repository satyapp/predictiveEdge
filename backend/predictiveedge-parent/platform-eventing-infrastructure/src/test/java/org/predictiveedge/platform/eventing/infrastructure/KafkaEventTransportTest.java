package org.predictiveedge.platform.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class KafkaEventTransportTest {
    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @SuppressWarnings("unchecked")
    void publishesEnvelopeWithGovernedKeyTimestampAndHeaders() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        AtomicReference<ProducerRecord<String, String>> captured = new AtomicReference<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            captured.set(record);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition(record.topic(), 2), 41, 0, record.timestamp(), 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
        });
        KafkaEventTransport transport = new KafkaEventTransport(
                kafka, JSON, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(1));

        var receipt = transport.publish(new EventPublication("pe.trade-guardian.v1", event()));

        assertThat(receipt.topic()).isEqualTo("pe.trade-guardian.v1");
        assertThat(receipt.partition()).isEqualTo(2);
        assertThat(receipt.offset()).isEqualTo(41);
        assertThat(receipt.publishedAt()).isEqualTo(NOW);
        ProducerRecord<String, String> record = captured.get();
        assertThat(record.key()).isEqualTo("recommendation-1");
        assertThat(record.timestamp()).isEqualTo(NOW.toEpochMilli());
        EventEnvelope wireEvent = JSON.readValue(record.value(), EventEnvelope.class);
        assertThat(wireEvent.metadata().publishedAt()).isEqualTo(NOW);
        assertThat(record.headers().lastHeader("pe-event-id")).isNotNull();
        assertThat(record.headers().lastHeader("pe-payload-hash")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsBrokerFailureWithoutLeakingItsMessageIntoTheApplicationContract() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("secret-broker-detail"));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);
        KafkaEventTransport transport = new KafkaEventTransport(
                kafka, JSON, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(1));

        assertThatThrownBy(() -> transport.publish(new EventPublication("pe.trade-guardian.v1", event())))
                .isInstanceOf(EventPublicationException.class)
                .hasMessage("Kafka publication failed")
                .hasMessageNotContaining("secret-broker-detail");
    }

    private static EventEnvelope event() {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        EventMetadata metadata = new EventMetadata(
                eventId, "Guardian.HealthChanged", new SchemaVersion(1, 0), "trade-guardian",
                "Recommendation", "recommendation-1", 1, "recommendation-1",
                NOW, NOW, NOW, null, NOW, NOW, eventId, eventId,
                null, "recommendation-1", "trade-1", "account-1", List.of(), null);
        return EventEnvelope.create(
                metadata, JsonNodeFactory.instance.objectNode().put("health", "ON_TRACK"),
                DataClassification.CONFIDENTIAL);
    }
}
