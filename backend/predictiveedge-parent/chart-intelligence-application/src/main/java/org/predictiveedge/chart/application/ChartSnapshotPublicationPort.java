package org.predictiveedge.chart.application;

import java.util.UUID;
import org.predictiveedge.chart.domain.ChartSnapshot;

/** Append-only publication boundary for a tenant-owned immutable chart snapshot. */
@FunctionalInterface
public interface ChartSnapshotPublicationPort {
    boolean append(UUID userId, ChartSnapshot snapshot);
}
