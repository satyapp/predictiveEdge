package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;

@FunctionalInterface
public interface ExecutionEvidencePublicationPort {
    boolean append(ExecutionEvidenceSnapshot snapshot);
}
