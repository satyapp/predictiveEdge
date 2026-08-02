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
import org.predictiveedge.platform.eventing.application.PublicationFailure;
import org.predictiveedge.platform.eventing.application.PublicationReceipt;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class JdbcEventOutboxTest {
    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    private static final UUID OUTBOX_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID LEASE_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Test
    void stagesTheCompleteEnvelopeAsJson() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);
        JdbcEventOutbox outbox = outbox(jdbc);

        outbox.stage(new EventPublication("pe.trades.v1", event()));

        assertThat(jdbc.statements).singleElement().asString()
                .contains("insert into eventing.outbox_event");
        Object[] arguments = jdbc.arguments.getFirst();
        assertThat(arguments[1]).isEqualTo(event().metadata().eventId());
        assertThat(arguments[7]).isEqualTo("pe.trades.v1");
        assertThat(arguments[8].toString()).contains("Trade.Registered", "trade-1", "payloadHash");
        assertThat(arguments[9]).isEqualTo(event().payloadHash());
    }

    @Test
    void refusesToCompleteAClaimWhoseLeaseWasLost() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(0);

        assertThatThrownBy(() -> outbox(jdbc).markPublished(
                OUTBOX_ID, LEASE_ID, new PublicationReceipt("pe.trades.v1", 0, 1, NOW)))
                .isInstanceOf(OutboxLeaseLostException.class)
                .hasMessageContaining(OUTBOX_ID.toString());
    }

    @Test
    void releasesTheOwnedLeaseAndSchedulesFailedPublication() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);

        outbox(jdbc).markFailed(
                OUTBOX_ID, LEASE_ID,
                new PublicationFailure("TimeoutException", NOW, NOW.plusSeconds(10)));

        assertThat(jdbc.statements).singleElement().asString().contains("publish_state='FAILED'");
        assertThat(jdbc.arguments.getFirst()).contains(
                "TimeoutException", OUTBOX_ID, LEASE_ID);
    }

    private static JdbcEventOutbox outbox(JdbcTemplate jdbc) {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        return new JdbcEventOutbox(jdbc, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EventEnvelope event() {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        EventMetadata metadata = new EventMetadata(
                eventId, "Trade.Registered", new SchemaVersion(1, 0), "trade-registration",
                "Trade", "trade-1", 1, "trade-1", NOW, NOW, NOW, null, NOW, NOW,
                eventId, eventId, null, "recommendation-1", "trade-1", "account-1",
                List.of(), null);
        return EventEnvelope.create(
                metadata, JsonNodeFactory.instance.objectNode().put("tradeId", "trade-1"),
                DataClassification.CONFIDENTIAL);
    }

    private static final class ScriptedJdbcTemplate extends JdbcTemplate {
        private final Queue<Integer> updateResults = new ArrayDeque<>();
        private final List<String> statements = new ArrayList<>();
        private final List<Object[]> arguments = new ArrayList<>();

        private ScriptedJdbcTemplate(Integer... results) {
            updateResults.addAll(List.of(results));
        }

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            arguments.add(args);
            return updateResults.remove();
        }
    }
}
