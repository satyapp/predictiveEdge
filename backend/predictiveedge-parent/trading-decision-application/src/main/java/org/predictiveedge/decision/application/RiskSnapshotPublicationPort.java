package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.RiskSnapshot;

@FunctionalInterface
public interface RiskSnapshotPublicationPort {
    boolean append(RiskSnapshot snapshot);
}
