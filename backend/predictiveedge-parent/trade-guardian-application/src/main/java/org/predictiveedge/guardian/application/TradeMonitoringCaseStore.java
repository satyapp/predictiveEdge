package org.predictiveedge.guardian.application;

import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.guardian.domain.TradeMonitoringEvent;

/** Persistence port; state and its governed lifecycle event are committed atomically. */
public interface TradeMonitoringCaseStore {
    boolean create(TradeMonitoringCase monitoringCase, TradeMonitoringEvent event);

    Optional<TradeMonitoringCase> findById(UUID monitoringCaseId);

    boolean replace(TradeMonitoringCase monitoringCase, long expectedVersion, TradeMonitoringEvent event);
}
