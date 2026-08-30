package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.PortfolioSnapshot;

@FunctionalInterface
public interface PortfolioSnapshotPublicationPort {
    boolean append(PortfolioSnapshot snapshot);
}
