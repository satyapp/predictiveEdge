package org.predictiveedge.decision.application;

import java.time.Instant;
import java.util.Optional;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

@FunctionalInterface
public interface ExecutionEvidenceQueryPort {
    Optional<ExecutionEvidenceSnapshot> findLatestEvidence(ShadowScope scope, Instant cutoff);
}
