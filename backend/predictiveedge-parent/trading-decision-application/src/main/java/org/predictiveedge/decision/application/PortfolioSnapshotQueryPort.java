package org.predictiveedge.decision.application;

import java.time.Instant;
import java.util.Optional;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

@FunctionalInterface
public interface PortfolioSnapshotQueryPort {
    Optional<PortfolioSnapshot> findLatestPortfolio(ShadowScope scope, Instant cutoff);
}
