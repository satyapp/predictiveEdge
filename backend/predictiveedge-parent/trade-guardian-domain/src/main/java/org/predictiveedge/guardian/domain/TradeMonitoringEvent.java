package org.predictiveedge.guardian.domain;

import java.util.Objects;

/** Governed lifecycle fact persisted atomically with its monitoring-case snapshot. */
public record TradeMonitoringEvent(Type type, TradeMonitoringCase monitoringCase) {
    public TradeMonitoringEvent {
        Objects.requireNonNull(type, "Monitoring event type is required");
        Objects.requireNonNull(monitoringCase, "Monitoring case is required");
        boolean valid = switch (type) {
            case MANUAL_TRADE_REGISTERED -> monitoringCase.version() == 1
                    && monitoringCase.state() == MonitoringState.ACTIVE;
            case MONITORING_SUSPENDED -> monitoringCase.state() == MonitoringState.SUSPENDED;
            case MONITORING_RESUMED -> monitoringCase.version() > 1
                    && monitoringCase.state() == MonitoringState.ACTIVE;
            case MONITORING_COMPLETED -> monitoringCase.state() == MonitoringState.COMPLETED;
        };
        if (!valid) {
            throw new IllegalArgumentException("Monitoring event does not match the case lifecycle state");
        }
    }

    public enum Type {
        MANUAL_TRADE_REGISTERED("TradeGuardian.ManualTradeRegistered"),
        MONITORING_SUSPENDED("TradeGuardian.MonitoringSuspended"),
        MONITORING_RESUMED("TradeGuardian.MonitoringResumed"),
        MONITORING_COMPLETED("TradeGuardian.MonitoringCompleted");

        private final String eventType;

        Type(String eventType) {
            this.eventType = eventType;
        }

        public String eventType() {
            return eventType;
        }
    }
}
