package org.predictiveedge.decision.application;

import java.time.Instant;
import java.util.Optional;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

@FunctionalInterface
public interface RiskSnapshotQueryPort {
    Optional<RiskSnapshot> findLatestRisk(ShadowScope scope, Instant cutoff);
}
