package org.predictiveedge.marketintelligence.application;

import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;

/** Append-only publication boundary for a tenant-owned semantic Market Context. */
@FunctionalInterface
public interface MarketContextPublicationPort {
    boolean append(UUID userId, MarketContextSnapshot snapshot);
}
