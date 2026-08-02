package org.predictiveedge.guardian.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.predictiveedge.guardian.domain.InstrumentRef;
import org.predictiveedge.guardian.domain.ManualFill;
import org.predictiveedge.guardian.domain.TradeDirection;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent.Type;

/** Use cases for recording trader actions and controlling advisory monitoring only. */
public final class TradeGuardianService {
    private final TradeMonitoringCaseStore store;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public TradeGuardianService(TradeMonitoringCaseStore store, Clock clock, Supplier<UUID> identifiers) {
        this.store = Objects.requireNonNull(store, "Monitoring case store is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.identifiers = Objects.requireNonNull(identifiers, "Identifier supplier is required");
    }

    public TradeMonitoringCase registerManualTrade(RegisterManualTrade command) {
        Objects.requireNonNull(command, "Registration command is required");
        TradeMonitoringCase monitoringCase = TradeMonitoringCase.register(
                identifiers.get(), identifiers.get(), command.traderId(), command.recommendationId(),
                command.approvedTradePlanRef(), command.accountRef(),
                new InstrumentRef(command.venue(), command.symbol()), command.direction(),
                new ManualFill(command.quantity(), command.averageEntryPrice(), command.executedAt(),
                        command.externalExecutionRef()),
                clock.instant());
        if (!store.create(monitoringCase, new TradeMonitoringEvent(Type.MANUAL_TRADE_REGISTERED, monitoringCase))) {
            throw new TradeGuardianFailure(TradeGuardianFailure.Code.RECOMMENDATION_ALREADY_MONITORED,
                    "The recommendation already has a registered trade");
        }
        return monitoringCase;
    }

    public TradeMonitoringCase monitoringCase(UUID traderId, UUID monitoringCaseId) {
        return ownedCase(traderId, monitoringCaseId);
    }

    public TradeMonitoringCase suspendMonitoring(UUID traderId, UUID monitoringCaseId, String reason) {
        TradeMonitoringCase current = ownedCase(traderId, monitoringCaseId);
        return replace(current, current.suspend(reason, clock.instant()), Type.MONITORING_SUSPENDED);
    }

    public TradeMonitoringCase resumeMonitoring(UUID traderId, UUID monitoringCaseId) {
        TradeMonitoringCase current = ownedCase(traderId, monitoringCaseId);
        return replace(current, current.resume(clock.instant()), Type.MONITORING_RESUMED);
    }

    public TradeMonitoringCase completeManualTrade(CompleteManualTrade command) {
        Objects.requireNonNull(command, "Completion command is required");
        TradeMonitoringCase current = ownedCase(command.traderId(), command.monitoringCaseId());
        ManualFill exit = new ManualFill(command.quantity(), command.averageExitPrice(), command.executedAt(),
                command.externalExecutionRef());
        return replace(current, current.complete(exit, clock.instant()), Type.MONITORING_COMPLETED);
    }

    private TradeMonitoringCase ownedCase(UUID traderId, UUID monitoringCaseId) {
        Objects.requireNonNull(traderId, "Trader id is required");
        Objects.requireNonNull(monitoringCaseId, "Monitoring case id is required");
        return store.findById(monitoringCaseId)
                .filter(value -> value.traderId().equals(traderId))
                .orElseThrow(() -> new TradeGuardianFailure(TradeGuardianFailure.Code.MONITORING_CASE_NOT_FOUND,
                        "Trade monitoring case was not found"));
    }

    private TradeMonitoringCase replace(
            TradeMonitoringCase current, TradeMonitoringCase changed, Type eventType) {
        if (!store.replace(changed, current.version(), new TradeMonitoringEvent(eventType, changed))) {
            throw new TradeGuardianFailure(TradeGuardianFailure.Code.CONCURRENT_MODIFICATION,
                    "Trade monitoring case changed; reload it before retrying");
        }
        return changed;
    }

    public record RegisterManualTrade(
            UUID traderId,
            String recommendationId,
            String approvedTradePlanRef,
            String accountRef,
            String venue,
            String symbol,
            TradeDirection direction,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            Instant executedAt,
            String externalExecutionRef) {}

    public record CompleteManualTrade(
            UUID traderId,
            UUID monitoringCaseId,
            BigDecimal quantity,
            BigDecimal averageExitPrice,
            Instant executedAt,
            String externalExecutionRef) {}
}
