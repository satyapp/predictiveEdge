package org.predictiveedge.platform.eventing.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Transport-neutral identity, ordering, time, and trace metadata for a domain event. */
public record EventMetadata(
        UUID eventId,
        String eventType,
        SchemaVersion schemaVersion,
        String producer,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String partitionKey,
        Instant occurredAt,
        Instant effectiveAt,
        Instant availableAt,
        Instant publishedAt,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        UUID correlationId,
        UUID causationId,
        String traderIntentId,
        String recommendationId,
        String tradeId,
        String accountId,
        List<ContextReference> contextReferences,
        String evidenceManifestRef) {

    public EventMetadata {
        Objects.requireNonNull(eventId, "Event id is required");
        eventType = required(eventType, "Event type");
        Objects.requireNonNull(schemaVersion, "Schema version is required");
        producer = required(producer, "Producer is required");
        aggregateType = required(aggregateType, "Aggregate type");
        aggregateId = required(aggregateId, "Aggregate id");
        if (aggregateVersion < 1) {
            throw new IllegalArgumentException("Aggregate version must be positive");
        }
        partitionKey = required(partitionKey, "Partition key");
        Objects.requireNonNull(occurredAt, "Occurred time is required");
        Objects.requireNonNull(effectiveAt, "Effective time is required");
        Objects.requireNonNull(availableAt, "Available time is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(correlationId, "Correlation id is required");
        Objects.requireNonNull(causationId, "Causation id is required");
        if (publishedAt != null && publishedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("Publication time cannot precede occurrence time");
        }
        traderIntentId = optional(traderIntentId, "Trader intent id");
        recommendationId = optional(recommendationId, "Recommendation id");
        tradeId = optional(tradeId, "Trade id");
        accountId = optional(accountId, "Account id");
        evidenceManifestRef = optional(evidenceManifestRef, "Evidence manifest reference");
        contextReferences = contextReferences == null ? List.of() : List.copyOf(contextReferences);
    }

    @JsonIgnore
    public boolean isPointInTimeEligible() {
        return !effectiveAt.isAfter(analysisCutoff) && !availableAt.isAfter(knowledgeCutoff);
    }

    public EventMetadata publishedAt(Instant publicationTime) {
        Objects.requireNonNull(publicationTime, "Publication time is required");
        return new EventMetadata(eventId, eventType, schemaVersion, producer, aggregateType, aggregateId,
                aggregateVersion, partitionKey, occurredAt, effectiveAt, availableAt, publicationTime,
                analysisCutoff, knowledgeCutoff, correlationId, causationId, traderIntentId,
                recommendationId, tradeId, accountId, contextReferences, evidenceManifestRef);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String optional(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
