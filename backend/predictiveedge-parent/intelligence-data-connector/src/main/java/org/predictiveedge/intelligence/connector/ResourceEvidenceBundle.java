package org.predictiveedge.intelligence.connector;

import java.util.List;
import java.util.Objects;
import org.predictiveedge.decision.domain.DecisionResourceType;

public record ResourceEvidenceBundle(
        DecisionResourceType resourceType,
        EvidenceQuery query,
        List<ProviderEvidence> evidence) {
    public ResourceEvidenceBundle {
        Objects.requireNonNull(resourceType, "Decision resource type is required");
        Objects.requireNonNull(query, "Evidence query is required");
        evidence = List.copyOf(evidence);
    }
}
