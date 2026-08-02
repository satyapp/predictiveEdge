package org.predictiveedge.platform.eventing.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.application.EventTransport;
import org.predictiveedge.platform.eventing.application.PublicationReceipt;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Kafka transport using the envelope partition key and idempotent producer configuration. */
public final class KafkaEventTransport implements EventTransport {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration timeout;

    public KafkaEventTransport(
            KafkaTemplate<String, String> kafka,
            ObjectMapper json,
            Clock clock,
            Duration timeout) {
        this.kafka = Objects.requireNonNull(kafka, "Kafka template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Kafka publication timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Override
    public PublicationReceipt publish(EventPublication publication) {
        Objects.requireNonNull(publication, "Event publication is required");
        Instant publicationTime = clock.instant();
        EventEnvelope publishedEvent = publication.event().publishedAt(publicationTime);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                publication.destinationTopic(),
                null,
                publicationTime.toEpochMilli(),
                publishedEvent.metadata().partitionKey(),
                serialize(publishedEvent),
                headers(publishedEvent));
        try {
            var result = kafka.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            var metadata = result.getRecordMetadata();
            Instant acknowledgedAt = metadata.hasTimestamp()
                    ? Instant.ofEpochMilli(metadata.timestamp())
                    : publicationTime;
            return new PublicationReceipt(
                    metadata.topic(), metadata.partition(), metadata.offset(), acknowledgedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException("Kafka publication was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new EventPublicationException("Kafka publication failed", exception);
        }
    }

    private String serialize(EventEnvelope event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new EventPublicationException("Event envelope cannot be serialized", exception);
        }
    }

    private static Iterable<Header> headers(EventEnvelope event) {
        return List.of(
                header("pe-event-id", event.metadata().eventId().toString()),
                header("pe-event-type", event.metadata().eventType()),
                header("pe-schema-version", event.metadata().schemaVersion().toString()),
                header("pe-correlation-id", event.metadata().correlationId().toString()),
                header("pe-causation-id", event.metadata().causationId().toString()),
                header("pe-payload-hash", event.payloadHash()));
    }

    private static Header header(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
