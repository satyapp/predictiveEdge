package org.predictiveedge.guardian.application;

import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.guardian.domain.TradeMonitoringCase;

/** Persistence port; implementations enforce recommendation uniqueness and optimistic versioning atomically. */
public interface TradeMonitoringCaseStore {
    boolean create(TradeMonitoringCase monitoringCase);

    Optional<TradeMonitoringCase> findById(UUID monitoringCaseId);

    boolean replace(TradeMonitoringCase monitoringCase, long expectedVersion);
}
