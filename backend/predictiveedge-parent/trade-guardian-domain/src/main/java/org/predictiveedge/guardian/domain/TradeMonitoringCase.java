package org.predictiveedge.guardian.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable lifecycle for one manually executed trade linked to one recommendation. */
public record TradeMonitoringCase(
        UUID monitoringCaseId,
        UUID tradeId,
        UUID traderId,
        String recommendationId,
        String approvedTradePlanRef,
        String accountRef,
        InstrumentRef instrument,
        TradeDirection direction,
        ManualFill entryFill,
        MonitoringState state,
        long version,
        Instant registeredAt,
        Instant stateChangedAt,
        String suspensionReason,
        ManualFill exitFill) {

    public TradeMonitoringCase {
        Objects.requireNonNull(monitoringCaseId, "Monitoring case id is required");
        Objects.requireNonNull(tradeId, "Trade id is required");
        Objects.requireNonNull(traderId, "Trader id is required");
        recommendationId = required(recommendationId, "Recommendation id");
        approvedTradePlanRef = required(approvedTradePlanRef, "Approved Trade Plan reference");
        accountRef = required(accountRef, "Account reference");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(direction, "Trade direction is required");
        Objects.requireNonNull(entryFill, "Entry fill is required");
        Objects.requireNonNull(state, "Monitoring state is required");
        Objects.requireNonNull(registeredAt, "Registration time is required");
        Objects.requireNonNull(stateChangedAt, "State change time is required");
        if (version < 1) {
            throw new IllegalArgumentException("Version must be positive");
        }
        if (registeredAt.isBefore(entryFill.executedAt())) {
            throw new IllegalArgumentException("Registration cannot precede the entry execution");
        }
        if (stateChangedAt.isBefore(registeredAt)) {
            throw new IllegalArgumentException("State change cannot precede registration");
        }
        suspensionReason = optional(suspensionReason, "Suspension reason");
        if (state == MonitoringState.SUSPENDED && suspensionReason == null) {
            throw new IllegalArgumentException("Suspended monitoring requires a reason");
        }
        if (state != MonitoringState.SUSPENDED && suspensionReason != null) {
            throw new IllegalArgumentException("Only suspended monitoring may have a suspension reason");
        }
        if (state == MonitoringState.COMPLETED) {
            validateExit(entryFill, exitFill, stateChangedAt);
        } else if (exitFill != null) {
            throw new IllegalArgumentException("Only completed monitoring may have an exit fill");
        }
    }

    public static TradeMonitoringCase register(
            UUID monitoringCaseId,
            UUID tradeId,
            UUID traderId,
            String recommendationId,
            String approvedTradePlanRef,
            String accountRef,
            InstrumentRef instrument,
            TradeDirection direction,
            ManualFill entryFill,
            Instant registeredAt) {
        return new TradeMonitoringCase(monitoringCaseId, tradeId, traderId, recommendationId,
                approvedTradePlanRef, accountRef, instrument, direction, entryFill,
                MonitoringState.ACTIVE, 1, registeredAt, registeredAt, null, null);
    }

    public TradeMonitoringCase suspend(String reason, Instant suspendedAt) {
        requireState(MonitoringState.ACTIVE, "suspend");
        return transition(MonitoringState.SUSPENDED, suspendedAt, required(reason, "Suspension reason"), null);
    }

    public TradeMonitoringCase resume(Instant resumedAt) {
        requireState(MonitoringState.SUSPENDED, "resume");
        return transition(MonitoringState.ACTIVE, resumedAt, null, null);
    }

    public TradeMonitoringCase complete(ManualFill actualExitFill, Instant completedAt) {
        if (state == MonitoringState.COMPLETED) {
            throw new IllegalStateException("Completed monitoring cannot be completed again");
        }
        validateExit(entryFill, actualExitFill, completedAt);
        return transition(MonitoringState.COMPLETED, completedAt, null, actualExitFill);
    }

    private TradeMonitoringCase transition(
            MonitoringState nextState, Instant changedAt, String nextSuspensionReason, ManualFill nextExitFill) {
        Objects.requireNonNull(changedAt, "State change time is required");
        if (changedAt.isBefore(stateChangedAt)) {
            throw new IllegalArgumentException("State changes must be chronological");
        }
        return new TradeMonitoringCase(monitoringCaseId, tradeId, traderId, recommendationId,
                approvedTradePlanRef, accountRef, instrument, direction, entryFill, nextState,
                version + 1, registeredAt, changedAt, nextSuspensionReason, nextExitFill);
    }

    private void requireState(MonitoringState expected, String action) {
        if (state != expected) {
            throw new IllegalStateException("Cannot " + action + " monitoring while state is " + state);
        }
    }

    private static void validateExit(ManualFill entry, ManualFill exit, Instant completedAt) {
        Objects.requireNonNull(exit, "Exit fill is required");
        Objects.requireNonNull(completedAt, "Completion time is required");
        if (exit.quantity().compareTo(entry.quantity()) != 0) {
            throw new IllegalArgumentException("Initial monitoring supports full-position exits only");
        }
        if (exit.executedAt().isBefore(entry.executedAt())) {
            throw new IllegalArgumentException("Exit execution cannot precede entry execution");
        }
        if (completedAt.isBefore(exit.executedAt())) {
            throw new IllegalArgumentException("Completion cannot precede the exit execution");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String optional(String value, String name) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }
}
