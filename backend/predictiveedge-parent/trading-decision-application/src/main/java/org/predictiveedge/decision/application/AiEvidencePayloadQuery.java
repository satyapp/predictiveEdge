package org.predictiveedge.decision.application;

import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;

/** Resolves every immutable payload reference; missing payloads must fail closed. */
@FunctionalInterface
public interface AiEvidencePayloadQuery {
    AiModelEvidenceInput resolve(AITradingDecisionInputBundle bundle);
}
