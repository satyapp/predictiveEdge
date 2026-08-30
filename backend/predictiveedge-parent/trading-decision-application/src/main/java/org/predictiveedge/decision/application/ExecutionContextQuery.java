package org.predictiveedge.decision.application;

import java.time.Instant;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.ShadowScope;

/** Point-in-time execution-feasibility source. It has no order-write capability. */
@FunctionalInterface
public interface ExecutionContextQuery {
    ExecutionContext findLatest(ShadowScope scope, Instant cutoff);
}
