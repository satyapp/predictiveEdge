package org.predictiveedge.decision.application;

import java.time.Instant;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ShadowScope;

/** Point-in-time source for one mandatory intelligence resource. */
public interface DecisionResourceQuery {
    DecisionResourceType type();

    DecisionResource findLatest(ShadowScope scope, Instant cutoff);
}
