package org.predictiveedge.decision.application;

import java.time.Instant;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ShadowScope;

/** Append-only publication of the exact JSON payload referenced by a DecisionResource. */
@FunctionalInterface
public interface AiResourcePayloadPublicationPort {
    boolean append(ShadowScope scope, DecisionResourceType type, String payloadRef, String evidenceHash,
            Instant availableAt, String payloadJson);
}
