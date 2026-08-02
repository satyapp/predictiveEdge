package org.predictiveedge.platform.eventing.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.predictiveedge.platform.eventing.application.EventDelivery;
import org.predictiveedge.platform.eventing.application.EventHandler;
import org.predictiveedge.platform.eventing.application.InboxTransaction;
import org.predictiveedge.platform.eventing.application.ProcessingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

/** PostgreSQL inbox transaction that makes event handling idempotent per consumer. */
public final class JdbcInboxTransaction implements InboxTransaction {
    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final Clock clock;

    public JdbcInboxTransaction(JdbcTemplate jdbc, TransactionOperations transactions, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.transactions = Objects.requireNonNull(transactions, "Transaction operations are required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public ProcessingResult executeOnce(
            String consumerName, EventDelivery delivery, EventHandler handler) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("Consumer name is required");
        }
        Objects.requireNonNull(delivery, "Event delivery is required");
        Objects.requireNonNull(handler, "Event handler is required");
        return transactions.execute(status -> process(consumerName, delivery, handler));
    }

    private ProcessingResult process(
            String consumerName, EventDelivery delivery, EventHandler handler) {
        var metadata = delivery.event().metadata();
        int inserted = jdbc.update("""
                insert into eventing.inbox_event (
                  consumer_name,event_id,event_type,aggregate_id,aggregate_version,
                  topic,partition_id,broker_offset,received_at,processing_outcome)
                values (?,?,?,?,?,?,?,?,?,'PROCESSING')
                on conflict (consumer_name,event_id) do nothing
                """, consumerName, metadata.eventId(), metadata.eventType(), metadata.aggregateId(),
                metadata.aggregateVersion(), delivery.topic(), delivery.partition(), delivery.offset(),
                Timestamp.from(delivery.receivedAt()));
        if (inserted == 0) {
            return ProcessingResult.DUPLICATE;
        }

        handler.handle(delivery.event());
        Instant processedAt = clock.instant();
        int completed = jdbc.update("""
                update eventing.inbox_event set processed_at=?,processing_outcome='PROCESSED'
                where consumer_name=? and event_id=? and processing_outcome='PROCESSING'
                """, Timestamp.from(processedAt), consumerName, metadata.eventId());
        if (completed != 1) {
            throw new IllegalStateException("Inbox event lost during processing");
        }
        return ProcessingResult.PROCESSED;
    }
}
