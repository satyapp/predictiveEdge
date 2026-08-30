package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.ShadowDecisionCase;

@FunctionalInterface
public interface ShadowDecisionCaseStore {
    boolean append(ShadowDecisionCase decisionCase);
}
