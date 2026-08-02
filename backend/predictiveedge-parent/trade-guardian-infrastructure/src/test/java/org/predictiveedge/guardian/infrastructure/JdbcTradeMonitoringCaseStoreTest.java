package org.predictiveedge.guardian.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.guardian.domain.InstrumentRef;
import org.predictiveedge.guardian.domain.ManualFill;
import org.predictiveedge.guardian.domain.MonitoringState;
import org.predictiveedge.guardian.domain.TradeDirection;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent.Type;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class JdbcTradeMonitoringCaseStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-02T05:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Test
    void createsSnapshotAndStagesRegistrationEvent() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);
        CapturingPublisher publisher = new CapturingPublisher();
        TradeMonitoringCase monitoringCase = activeCase();

        boolean created = store(jdbc, publisher).create(monitoringCase,
                new TradeMonitoringEvent(Type.MANUAL_TRADE_REGISTERED, monitoringCase));

        assertThat(created).isTrue();
        assertThat(jdbc.statements).singleElement().asString().contains("guardian.trade_monitoring_case");
        assertThat(jdbc.arguments.getFirst()).contains(
                monitoringCase.monitoringCaseId(), monitoringCase.recommendationId(), "ACTIVE", 1L);
        EventPublication publication = publisher.publications.getFirst();
        assertThat(publication.destinationTopic()).isEqualTo("pe.trade-guardian.v1");
        assertThat(publication.event().metadata().eventType()).isEqualTo("TradeGuardian.ManualTradeRegistered");
        assertThat(publication.event().metadata().partitionKey()).isEqualTo("recommendation-1");
        assertThat(publication.event().classification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(publication.event().payload().path("entryFill").path("quantity").decimalValue())
                .isEqualByComparingTo("4");
    }

    @Test
    void duplicateCreateDoesNotStageAnEvent() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(0);
        CapturingPublisher publisher = new CapturingPublisher();
        TradeMonitoringCase monitoringCase = activeCase();

        boolean created = store(jdbc, publisher).create(monitoringCase,
                new TradeMonitoringEvent(Type.MANUAL_TRADE_REGISTERED, monitoringCase));

        assertThat(created).isFalse();
        assertThat(publisher.publications).isEmpty();
    }

    @Test
    void optimisticReplaceStagesOnlyAnAcceptedLifecycleChange() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);
        CapturingPublisher publisher = new CapturingPublisher();
        TradeMonitoringCase completed = activeCase().complete(
                new ManualFill(new BigDecimal("4"), new BigDecimal("1550"), NOW.plusSeconds(10), "exit-1"),
                NOW.plusSeconds(20));

        boolean replaced = store(jdbc, publisher).replace(completed, 1,
                new TradeMonitoringEvent(Type.MONITORING_COMPLETED, completed));

        assertThat(replaced).isTrue();
        assertThat(jdbc.arguments.getFirst()).contains(
                MonitoringState.COMPLETED.name(), 2L, completed.monitoringCaseId(), 1L);
        assertThat(publisher.publications.getFirst().event().metadata().eventType())
                .isEqualTo("TradeGuardian.MonitoringCompleted");
        assertThat(publisher.publications.getFirst().event().payload().path("exitFill").path("averagePrice")
                .decimalValue()).isEqualByComparingTo("1550");
    }

    @Test
    void reloadsTheImmutableSnapshot() throws Exception {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate();
        TradeMonitoringCase monitoringCase = activeCase();
        jdbc.querySnapshot = mapper().writeValueAsString(monitoringCase);

        Optional<TradeMonitoringCase> loaded = store(jdbc, new CapturingPublisher())
                .findById(monitoringCase.monitoringCaseId());

        assertThat(loaded).contains(monitoringCase);
    }

    @Test
    void rejectsAnEventForAnotherSnapshotBeforeWriting() {
        ScriptedJdbcTemplate jdbc = new ScriptedJdbcTemplate(1);
        TradeMonitoringCase current = activeCase();
        TradeMonitoringCase suspended = current.suspend("data stale", NOW.plusSeconds(1));

        assertThatIllegalArgumentException().isThrownBy(() -> store(jdbc, new CapturingPublisher()).replace(
                suspended, 1, new TradeMonitoringEvent(Type.MANUAL_TRADE_REGISTERED, current)))
                .withMessageContaining("persisted case snapshot");
        assertThat(jdbc.statements).isEmpty();
    }

    private static JdbcTradeMonitoringCaseStore store(
            JdbcTemplate jdbc, DomainEventPublisher publisher) {
        return new JdbcTradeMonitoringCaseStore(jdbc, mapper(), publisher, () -> EVENT_ID);
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static TradeMonitoringCase activeCase() {
        return TradeMonitoringCase.register(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "recommendation-1", "plan-v3", "account-1", new InstrumentRef("NSE", "RELIANCE"),
                TradeDirection.LONG, new ManualFill(new BigDecimal("4"), new BigDecimal("1500.25"),
                        NOW.minusSeconds(30), "entry-1"), NOW);
    }

    private static final class CapturingPublisher implements DomainEventPublisher {
        private final List<EventPublication> publications = new ArrayList<>();

        @Override
        public void stage(EventPublication publication) {
            publications.add(publication);
        }
    }

    private static final class ScriptedJdbcTemplate extends JdbcTemplate {
        private final Queue<Integer> results = new ArrayDeque<>();
        private final List<String> statements = new ArrayList<>();
        private final List<Object[]> arguments = new ArrayList<>();
        private String querySnapshot;

        private ScriptedJdbcTemplate(Integer... updateResults) {
            results.addAll(List.of(updateResults));
        }

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            arguments.add(args);
            return results.remove();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (querySnapshot == null) {
                return List.of();
            }
            try {
                ResultSet result = mock(ResultSet.class);
                when(result.getString("snapshot_json")).thenReturn(querySnapshot);
                return List.of(rowMapper.mapRow(result, 0));
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
