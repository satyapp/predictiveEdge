package org.predictiveedge.platform.eventing.contract;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/** Immutable, Kafka-independent envelope for versioned domain-event payloads. */
public record EventEnvelope(
        EventMetadata metadata,
        JsonNode payload,
        String payloadHash,
        DataClassification classification) {

    public EventEnvelope {
        Objects.requireNonNull(metadata, "Event metadata is required");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Event payload is required");
        }
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Payload hash must be a lowercase SHA-256 value");
        }
        if (!PayloadHasher.sha256(payload).equals(payloadHash)) {
            throw new IllegalArgumentException("Payload hash does not match the payload");
        }
        Objects.requireNonNull(classification, "Data classification is required");
        payload = payload.deepCopy();
    }

    public static EventEnvelope create(
            EventMetadata metadata, JsonNode payload, DataClassification classification) {
        return new EventEnvelope(metadata, payload, PayloadHasher.sha256(payload), classification);
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }

    @JsonIgnore
    public boolean isPointInTimeEligible() {
        return metadata.isPointInTimeEligible();
    }

    public EventEnvelope publishedAt(Instant publicationTime) {
        return new EventEnvelope(
                metadata.publishedAt(publicationTime), payload, payloadHash, classification);
    }
}
