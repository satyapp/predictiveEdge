package org.predictiveedge.platform.eventing.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.application.OutboxEntry;
import org.predictiveedge.platform.eventing.application.OutboxStore;
import org.predictiveedge.platform.eventing.application.PublicationFailure;
import org.predictiveedge.platform.eventing.application.PublicationReceipt;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** PostgreSQL transactional outbox and lease-based dispatcher store. */
public class JdbcEventOutbox implements DomainEventPublisher, OutboxStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public JdbcEventOutbox(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(EventPublication publication) {
        Objects.requireNonNull(publication, "Event publication is required");
        EventEnvelope event = publication.event();
        Instant createdAt = clock.instant();
        jdbc.update("""
                insert into eventing.outbox_event (
                  outbox_id,event_id,event_type,aggregate_type,aggregate_id,aggregate_version,
                  partition_key,destination_topic,envelope_json,payload_hash,schema_version,created_at,publish_state,
                  next_attempt_at,attempt_count)
                values (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'PENDING',?,0)
                """,
                UUID.randomUUID(), event.metadata().eventId(), event.metadata().eventType(),
                event.metadata().aggregateType(), event.metadata().aggregateId(),
                event.metadata().aggregateVersion(), event.metadata().partitionKey(),
                publication.destinationTopic(), serialize(event), event.payloadHash(),
                event.metadata().schemaVersion().toString(), Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    @Override
    public List<OutboxEntry> claimPending(int batchSize, Instant claimedAt, Duration leaseDuration) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Claim batch size must be positive");
        }
        Objects.requireNonNull(claimedAt, "Claim time is required");
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        UUID leaseId = UUID.randomUUID();
        Instant leaseExpiresAt = claimedAt.plus(leaseDuration);
        return jdbc.query("""
                with pending as (
                  select outbox_id from eventing.outbox_event
                  where publish_state in ('PENDING','FAILED','CLAIMED')
                    and next_attempt_at<=?
                    and (lease_expires_at is null or lease_expires_at<=?)
                  order by created_at,outbox_id
                  for update skip locked limit ?
                )
                update eventing.outbox_event event
                set publish_state='CLAIMED',lease_id=?,lease_expires_at=?,attempt_count=attempt_count+1
                from pending where event.outbox_id=pending.outbox_id
                returning event.outbox_id,event.destination_topic,event.envelope_json::text,event.attempt_count
                """, statement -> {
                    statement.setTimestamp(1, Timestamp.from(claimedAt));
                    statement.setTimestamp(2, Timestamp.from(claimedAt));
                    statement.setInt(3, batchSize);
                    statement.setObject(4, leaseId);
                    statement.setTimestamp(5, Timestamp.from(leaseExpiresAt));
                }, (result, row) -> new OutboxEntry(
                        result.getObject("outbox_id", UUID.class),
                        leaseId,
                        new EventPublication(
                                result.getString("destination_topic"),
                                deserialize(result.getString("envelope_json"))),
                        result.getInt("attempt_count") - 1));
    }

    @Override
    public void markPublished(UUID outboxId, UUID leaseId, PublicationReceipt receipt) {
        Objects.requireNonNull(receipt, "Publication receipt is required");
        int changed = jdbc.update("""
                update eventing.outbox_event
                set publish_state='PUBLISHED',published_at=?,broker_topic=?,broker_partition=?,broker_offset=?,
                    lease_id=null,lease_expires_at=null,last_failure=null
                where outbox_id=? and lease_id=? and publish_state='CLAIMED'
                """, Timestamp.from(receipt.publishedAt()), receipt.topic(), receipt.partition(), receipt.offset(),
                outboxId, leaseId);
        requireLease(changed, outboxId);
    }

    @Override
    public void markFailed(UUID outboxId, UUID leaseId, PublicationFailure failure) {
        Objects.requireNonNull(failure, "Publication failure is required");
        int changed = jdbc.update("""
                update eventing.outbox_event
                set publish_state='FAILED',last_failure=?,next_attempt_at=?,lease_id=null,lease_expires_at=null
                where outbox_id=? and lease_id=? and publish_state='CLAIMED'
                """, failure.errorType(), Timestamp.from(failure.retryAt()), outboxId, leaseId);
        requireLease(changed, outboxId);
    }

    private String serialize(EventEnvelope event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event envelope cannot be serialized", exception);
        }
    }

    private EventEnvelope deserialize(String value) {
        try {
            return json.readValue(value, EventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored event envelope is invalid", exception);
        }
    }

    private static void requireLease(int changed, UUID outboxId) {
        if (changed != 1) {
            throw new OutboxLeaseLostException(outboxId);
        }
    }
}
