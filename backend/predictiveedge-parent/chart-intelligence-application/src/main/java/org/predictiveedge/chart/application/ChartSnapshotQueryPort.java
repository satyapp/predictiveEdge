package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.chart.domain.ChartSnapshot;

/** Point-in-time read boundary for immutable, tenant-owned chart snapshots. */
@FunctionalInterface
public interface ChartSnapshotQueryPort {
    Optional<ChartSnapshot> findLatest(UUID userId, String venue, String instrumentId, Instant cutoff);
}
