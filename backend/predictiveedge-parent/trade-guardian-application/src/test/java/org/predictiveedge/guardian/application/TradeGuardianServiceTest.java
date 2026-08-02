package org.predictiveedge.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.guardian.domain.MonitoringState;
import org.predictiveedge.guardian.domain.TradeDirection;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent;

class TradeGuardianServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final UUID TRADER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final InMemoryStore store = new InMemoryStore();
    private TradeGuardianService service;

    @BeforeEach
    void setUp() {
        UUID caseId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID tradeId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        java.util.ArrayDeque<UUID> ids = new java.util.ArrayDeque<>(java.util.List.of(caseId, tradeId));
        service = new TradeGuardianService(store, Clock.fixed(NOW, ZoneOffset.UTC), ids::remove);
    }

    @Test
    void registersManualFillAgainstRecommendationAndTradePlan() {
        TradeMonitoringCase registered = service.registerManualTrade(registration("recommendation-1"));

        assertThat(registered.recommendationId()).isEqualTo("recommendation-1");
        assertThat(registered.approvedTradePlanRef()).isEqualTo("plan-v3");
        assertThat(registered.instrument().venue()).isEqualTo("NSE");
        assertThat(registered.entryFill().averagePrice()).isEqualByComparingTo("1500.25");
        assertThat(registered.state()).isEqualTo(MonitoringState.ACTIVE);
        assertThat(store.findById(registered.monitoringCaseId())).contains(registered);
    }

    @Test
    void preventsTwoRegisteredTradesForOneRecommendation() {
        service.registerManualTrade(registration("recommendation-1"));
        service = new TradeGuardianService(store, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID);

        assertThatThrownBy(() -> service.registerManualTrade(registration("recommendation-1")))
                .isInstanceOfSatisfying(TradeGuardianFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                TradeGuardianFailure.Code.RECOMMENDATION_ALREADY_MONITORED));
    }

    @Test
    void returnsOnlyTheOwningTradersMonitoringCase() {
        TradeMonitoringCase registered = service.registerManualTrade(registration("recommendation-1"));

        assertThat(service.monitoringCase(TRADER_ID, registered.monitoringCaseId())).isEqualTo(registered);
        assertThatThrownBy(() -> service.monitoringCase(UUID.randomUUID(), registered.monitoringCaseId()))
                .isInstanceOfSatisfying(TradeGuardianFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                TradeGuardianFailure.Code.MONITORING_CASE_NOT_FOUND));
    }

    @Test
    void traderCanSuspendResumeAndCompleteTheRegisteredTrade() {
        TradeMonitoringCase registered = service.registerManualTrade(registration("recommendation-1"));
        TradeMonitoringCase suspended = service.suspendMonitoring(TRADER_ID, registered.monitoringCaseId(),
                "Waiting for fresh prices");
        TradeMonitoringCase resumed = service.resumeMonitoring(TRADER_ID, registered.monitoringCaseId());
        TradeMonitoringCase completed = service.completeManualTrade(new TradeGuardianService.CompleteManualTrade(
                TRADER_ID, registered.monitoringCaseId(), new BigDecimal("4"), new BigDecimal("1535"),
                NOW, "exit-456"));

        assertThat(suspended.state()).isEqualTo(MonitoringState.SUSPENDED);
        assertThat(resumed.state()).isEqualTo(MonitoringState.ACTIVE);
        assertThat(completed.state()).isEqualTo(MonitoringState.COMPLETED);
        assertThat(completed.version()).isEqualTo(4);
    }

    @Test
    void doesNotRevealAnotherTradersMonitoringCase() {
        TradeMonitoringCase registered = service.registerManualTrade(registration("recommendation-1"));

        assertThatThrownBy(() -> service.suspendMonitoring(UUID.randomUUID(), registered.monitoringCaseId(), "x"))
                .isInstanceOfSatisfying(TradeGuardianFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                TradeGuardianFailure.Code.MONITORING_CASE_NOT_FOUND));
    }

    @Test
    void reportsOptimisticConcurrencyConflict() {
        TradeMonitoringCase registered = service.registerManualTrade(registration("recommendation-1"));
        store.rejectReplacements = true;

        assertThatThrownBy(() -> service.suspendMonitoring(TRADER_ID, registered.monitoringCaseId(), "data stale"))
                .isInstanceOfSatisfying(TradeGuardianFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                TradeGuardianFailure.Code.CONCURRENT_MODIFICATION));
    }

    private static TradeGuardianService.RegisterManualTrade registration(String recommendationId) {
        return new TradeGuardianService.RegisterManualTrade(TRADER_ID, recommendationId, "plan-v3", "account-1",
                "nse", "reliance", TradeDirection.LONG, new BigDecimal("4"), new BigDecimal("1500.25"),
                NOW.minusSeconds(30), "entry-123");
    }

    private static final class InMemoryStore implements TradeMonitoringCaseStore {
        private final Map<UUID, TradeMonitoringCase> cases = new HashMap<>();
        private boolean rejectReplacements;

        @Override
        public boolean create(TradeMonitoringCase monitoringCase, TradeMonitoringEvent event) {
            boolean duplicate = cases.values().stream().anyMatch(existing ->
                    existing.recommendationId().equals(monitoringCase.recommendationId()));
            if (duplicate) {
                return false;
            }
            cases.put(monitoringCase.monitoringCaseId(), monitoringCase);
            return true;
        }

        @Override
        public Optional<TradeMonitoringCase> findById(UUID monitoringCaseId) {
            return Optional.ofNullable(cases.get(monitoringCaseId));
        }

        @Override
        public boolean replace(
                TradeMonitoringCase monitoringCase, long expectedVersion, TradeMonitoringEvent event) {
            TradeMonitoringCase current = cases.get(monitoringCase.monitoringCaseId());
            if (rejectReplacements || current == null || current.version() != expectedVersion) {
                return false;
            }
            cases.put(monitoringCase.monitoringCaseId(), monitoringCase);
            return true;
        }
    }
}
