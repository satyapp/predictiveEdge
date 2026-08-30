package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete immutable input passed to the sole AI recommendation authority. */
public record AITradingDecisionInputBundle(
        String bundleId,
        ShadowScope scope,
        String traderIntentId,
        Instant assembledAt,
        PointInTimeEvidenceManifest evidenceManifest,
        ExecutionContext executionContext,
        Map<DecisionResourceType, DecisionResource> resources) {

    private static final Set<DecisionResourceType> REQUIRED = Set.of(DecisionResourceType.values());

    public AITradingDecisionInputBundle {
        bundleId = required(bundleId, "Bundle id");
        Objects.requireNonNull(scope, "Shadow scope is required");
        traderIntentId = required(traderIntentId, "Trader intent id");
        Objects.requireNonNull(assembledAt, "Assembled-at time is required");
        Objects.requireNonNull(evidenceManifest, "Evidence manifest is required");
        Objects.requireNonNull(executionContext, "Execution context is required");
        Objects.requireNonNull(resources, "Resources are required");
        EnumMap<DecisionResourceType, DecisionResource> copy = new EnumMap<>(DecisionResourceType.class);
        resources.forEach((type, resource) -> {
            Objects.requireNonNull(type, "Resource type cannot be null");
            Objects.requireNonNull(resource, "Decision resource cannot be null");
            if (type != resource.type()) throw new IllegalArgumentException("Resource map key must match its type");
            scope.requireMatches(resource.userId(), resource.instrument());
            copy.put(type, resource);
        });
        if (!copy.keySet().containsAll(REQUIRED)) {
            throw new IllegalArgumentException("All twelve decision resources are required");
        }
        resources = Map.copyOf(copy);
    }

    public boolean isReady() {
        return evidenceManifest.isReady()
                && executionContext.isUsableAt(assembledAt)
                && resources.values().stream().allMatch(resource -> resource.isUsableAt(assembledAt));
    }

    public UUID userId() { return scope.userId(); }
    public InstrumentRef instrument() { return scope.instrument(); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
