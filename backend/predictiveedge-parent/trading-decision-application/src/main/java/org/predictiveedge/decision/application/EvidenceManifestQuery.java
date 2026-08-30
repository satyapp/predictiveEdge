package org.predictiveedge.decision.application;

import java.time.Instant;
import java.util.Map;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.ShadowScope;

/** Builds the causal manifest only after all mandatory resources have been resolved. */
@FunctionalInterface
public interface EvidenceManifestQuery {
    PointInTimeEvidenceManifest create(
            ShadowScope scope,
            Instant cutoff,
            Map<DecisionResourceType, DecisionResource> resources);
}
