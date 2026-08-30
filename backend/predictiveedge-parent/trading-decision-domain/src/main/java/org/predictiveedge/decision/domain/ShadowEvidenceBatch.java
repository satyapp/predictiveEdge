package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Append-only handoff from ingestion/resource builders to the shadow decision workflow. */
public record ShadowEvidenceBatch(
        String batchId,
        ShadowScope scope,
        Instant capturedAt,
        PointInTimeEvidenceManifest evidenceManifest,
        ExecutionContext executionContext,
        Map<DecisionResourceType, DecisionResource> resources) {

    private static final Set<DecisionResourceType> REQUIRED = Set.of(DecisionResourceType.values());

    public ShadowEvidenceBatch {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("Batch id is required");
        batchId = batchId.trim();
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(capturedAt, "Captured-at time is required");
        Objects.requireNonNull(evidenceManifest, "Evidence manifest is required");
        Objects.requireNonNull(executionContext, "Execution context is required");
        Objects.requireNonNull(resources, "Decision resources are required");
        EnumMap<DecisionResourceType, DecisionResource> copy = new EnumMap<>(DecisionResourceType.class);
        resources.forEach((type, resource) -> {
            Objects.requireNonNull(type, "Resource type cannot be null");
            Objects.requireNonNull(resource, "Decision resource cannot be null");
            if (type != resource.type()) throw new IllegalArgumentException("Resource map key must match its type");
            scope.requireMatches(resource.userId(), resource.instrument());
            copy.put(type, resource);
        });
        if (!copy.keySet().containsAll(REQUIRED)) throw new IllegalArgumentException("All twelve decision resources are required");
        resources = Map.copyOf(copy);
    }

    public Instant validUntil() {
        return resources.values().stream().map(DecisionResource::validUntil)
                .reduce(executionContext.validUntil(), (left, right) -> left.isBefore(right) ? left : right);
    }

    public AITradingDecisionInputBundle toBundle(String bundleId, String traderIntentId, Instant assembledAt) {
        if (assembledAt.isBefore(capturedAt)) throw new IllegalArgumentException("Bundle cannot be assembled before capture");
        return new AITradingDecisionInputBundle(bundleId, scope, traderIntentId, assembledAt,
                evidenceManifest, executionContext, resources);
    }
}
