package org.predictiveedge.platform.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.platform.eventing.application.EventDelivery;
import org.predictiveedge.platform.eventing.application.ProcessingResult;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class JdbcInboxTransactionTest {
    private static final Instant NOW = Instant.parse("2026-07-31T07:00:00Z");

    @Test
    void insertsBeforeHandlingAndMarksProcessedAfterward() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1, 1);
        JdbcInboxTransaction inbox = inbox(jdbc);
        List<String> order = new ArrayList<>();

        ProcessingResult result = inbox.executeOnce("guardian-v1", delivery(), event -> {
            assertThat(jdbc.statements).hasSize(1);
            order.add("handled");
        });

        assertThat(result).isEqualTo(ProcessingResult.PROCESSED);
        assertThat(jdbc.statements).hasSize(2);
        assertThat(jdbc.statements.get(0)).contains("insert into eventing.inbox_event");
        assertThat(jdbc.statements.get(1)).contains("processing_outcome='PROCESSED'");
        assertThat(order).containsExactly("handled");
    }

    @Test
    void duplicateDeliveryDoesNotInvokeTheHandler() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(0);
        JdbcInboxTransaction inbox = inbox(jdbc);
        List<EventEnvelope> handled = new ArrayList<>();

        ProcessingResult result = inbox.executeOnce("guardian-v1", delivery(), handled::add);

        assertThat(result).isEqualTo(ProcessingResult.DUPLICATE);
        assertThat(handled).isEmpty();
        assertThat(jdbc.statements).hasSize(1);
    }

    @Test
    void handlerFailureDoesNotMarkTheInboxRecordAsProcessed() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);
        JdbcInboxTransaction inbox = inbox(jdbc);

        assertThatThrownBy(() -> inbox.executeOnce(
                "guardian-v1", delivery(), event -> { throw new IllegalStateException("handler failed"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler failed");
        assertThat(jdbc.statements).hasSize(1);
    }

    @Test
    void missingInboxRowFailsTheTransactionInsteadOfAcknowledgingTheEvent() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1, 0);

        assertThatThrownBy(() -> inbox(jdbc).executeOnce("guardian-v1", delivery(), event -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lost");
    }

    private static JdbcInboxTransaction inbox(JdbcTemplate jdbc) {
        return new JdbcInboxTransaction(
                jdbc,
                new ImmediateTransactions(),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
    }

    private static EventDelivery delivery() {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        EventMetadata metadata = new EventMetadata(
                eventId, "Trade.Registered", new SchemaVersion(1, 0), "trade-registration",
                "Trade", "trade-1", 1, "trade-1", NOW, NOW, NOW, NOW, NOW, NOW,
                eventId, eventId, null, "recommendation-1", "trade-1", "account-1",
                List.of(), null);
        EventEnvelope event = EventEnvelope.create(
                metadata, JsonNodeFactory.instance.objectNode().put("tradeId", "trade-1"),
                DataClassification.CONFIDENTIAL);
        return new EventDelivery(event, "pe.trades.v1", 2, 99, NOW);
    }

    private static final class ScriptedJdbcTemplate extends JdbcTemplate {
        private final Queue<Integer> updateResults = new ArrayDeque<>();
        private final List<String> statements = new ArrayList<>();

        private ScriptedJdbcTemplate(Integer... results) {
            updateResults.addAll(List.of(results));
        }

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            return updateResults.remove();
        }
    }

    private static final class ImmediateTransactions implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            TransactionStatus status = new SimpleTransactionStatus();
            return action.doInTransaction(status);
        }
    }
}
