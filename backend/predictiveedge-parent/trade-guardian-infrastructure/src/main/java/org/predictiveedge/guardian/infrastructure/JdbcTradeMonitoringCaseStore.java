package org.predictiveedge.guardian.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.predictiveedge.guardian.application.TradeMonitoringCaseStore;
import org.predictiveedge.guardian.domain.ManualFill;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** PostgreSQL snapshot store that stages every accepted lifecycle change in the same transaction. */
public class JdbcTradeMonitoringCaseStore implements TradeMonitoringCaseStore {
    static final String TOPIC = "pe.trade-guardian.v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DomainEventPublisher events;
    private final Supplier<UUID> eventIds;

    public JdbcTradeMonitoringCaseStore(
            JdbcTemplate jdbc, ObjectMapper json, DomainEventPublisher events, Supplier<UUID> eventIds) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.events = Objects.requireNonNull(events, "Domain event publisher is required");
        this.eventIds = Objects.requireNonNull(eventIds, "Event id supplier is required");
    }

    @Override
    @Transactional
    public boolean create(TradeMonitoringCase monitoringCase, TradeMonitoringEvent event) {
        requireMatchingEvent(monitoringCase, event);
        int changed = jdbc.update("""
                insert into guardian.trade_monitoring_case (
                  monitoring_case_id,trade_id,trader_id,recommendation_id,approved_trade_plan_ref,
                  account_ref,venue,symbol,direction,monitoring_state,aggregate_version,snapshot_json,
                  registered_at,state_changed_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                on conflict do nothing
                """, monitoringCase.monitoringCaseId(), monitoringCase.tradeId(), monitoringCase.traderId(),
                monitoringCase.recommendationId(), monitoringCase.approvedTradePlanRef(), monitoringCase.accountRef(),
                monitoringCase.instrument().venue(), monitoringCase.instrument().symbol(),
                monitoringCase.direction().name(), monitoringCase.state().name(), monitoringCase.version(),
                serialize(monitoringCase), Timestamp.from(monitoringCase.registeredAt()),
                Timestamp.from(monitoringCase.stateChangedAt()));
        if (changed == 1) {
            events.stage(publication(event));
            return true;
        }
        return false;
    }

    @Override
    public Optional<TradeMonitoringCase> findById(UUID monitoringCaseId) {
        Objects.requireNonNull(monitoringCaseId, "Monitoring case id is required");
        return jdbc.query("""
                select snapshot_json::text from guardian.trade_monitoring_case where monitoring_case_id=?
                """, this::mapSnapshot, monitoringCaseId).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean replace(
            TradeMonitoringCase monitoringCase, long expectedVersion, TradeMonitoringEvent event) {
        requireMatchingEvent(monitoringCase, event);
        int changed = jdbc.update("""
                update guardian.trade_monitoring_case
                set monitoring_state=?,aggregate_version=?,snapshot_json=?::jsonb,state_changed_at=?
                where monitoring_case_id=? and aggregate_version=?
                """, monitoringCase.state().name(), monitoringCase.version(), serialize(monitoringCase),
                Timestamp.from(monitoringCase.stateChangedAt()), monitoringCase.monitoringCaseId(), expectedVersion);
        if (changed == 1) {
            events.stage(publication(event));
            return true;
        }
        return false;
    }

    private TradeMonitoringCase mapSnapshot(ResultSet result, int rowNumber) throws SQLException {
        try {
            return json.readValue(result.getString("snapshot_json"), TradeMonitoringCase.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Trade Guardian snapshot is invalid", exception);
        }
    }

    private EventPublication publication(TradeMonitoringEvent event) {
        TradeMonitoringCase monitoringCase = event.monitoringCase();
        UUID eventId = Objects.requireNonNull(eventIds.get(), "Generated event id is required");
        EventMetadata metadata = new EventMetadata(
                eventId, event.type().eventType(), new SchemaVersion(1, 0), "trade-guardian",
                "TradeMonitoringCase", monitoringCase.monitoringCaseId().toString(), monitoringCase.version(),
                monitoringCase.recommendationId(), monitoringCase.stateChangedAt(), monitoringCase.stateChangedAt(),
                monitoringCase.stateChangedAt(), null, monitoringCase.stateChangedAt(),
                monitoringCase.stateChangedAt(), eventId, eventId, null, monitoringCase.recommendationId(),
                monitoringCase.tradeId().toString(), monitoringCase.accountRef(), List.of(), null);
        return new EventPublication(TOPIC,
                EventEnvelope.create(metadata, payload(monitoringCase), DataClassification.CONFIDENTIAL));
    }

    private ObjectNode payload(TradeMonitoringCase monitoringCase) {
        ObjectNode payload = json.createObjectNode();
        payload.put("monitoringCaseId", monitoringCase.monitoringCaseId().toString());
        payload.put("tradeId", monitoringCase.tradeId().toString());
        payload.put("traderId", monitoringCase.traderId().toString());
        payload.put("recommendationId", monitoringCase.recommendationId());
        payload.put("approvedTradePlanRef", monitoringCase.approvedTradePlanRef());
        payload.put("accountRef", monitoringCase.accountRef());
        payload.put("venue", monitoringCase.instrument().venue());
        payload.put("symbol", monitoringCase.instrument().symbol());
        payload.put("direction", monitoringCase.direction().name());
        payload.put("monitoringState", monitoringCase.state().name());
        payload.put("aggregateVersion", monitoringCase.version());
        payload.set("entryFill", fill(monitoringCase.entryFill()));
        if (monitoringCase.suspensionReason() != null) {
            payload.put("suspensionReason", monitoringCase.suspensionReason());
        }
        if (monitoringCase.exitFill() != null) {
            payload.set("exitFill", fill(monitoringCase.exitFill()));
        }
        return payload;
    }

    private ObjectNode fill(ManualFill fill) {
        ObjectNode value = json.createObjectNode();
        value.put("quantity", fill.quantity());
        value.put("averagePrice", fill.averagePrice());
        value.put("executedAt", fill.executedAt().toString());
        if (fill.externalExecutionRef() != null) {
            value.put("externalExecutionRef", fill.externalExecutionRef());
        }
        return value;
    }

    private String serialize(TradeMonitoringCase monitoringCase) {
        try {
            return json.writeValueAsString(monitoringCase);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Trade Guardian snapshot cannot be serialized", exception);
        }
    }

    private static void requireMatchingEvent(
            TradeMonitoringCase monitoringCase, TradeMonitoringEvent event) {
        Objects.requireNonNull(monitoringCase, "Monitoring case is required");
        Objects.requireNonNull(event, "Monitoring event is required");
        if (!monitoringCase.equals(event.monitoringCase())) {
            throw new IllegalArgumentException("Monitoring event must describe the persisted case snapshot");
        }
    }
}
