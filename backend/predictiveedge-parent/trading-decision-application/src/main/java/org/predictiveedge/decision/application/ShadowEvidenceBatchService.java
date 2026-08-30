package org.predictiveedge.decision.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.PointInTimeEvidenceManifest;
import org.predictiveedge.decision.domain.ShadowEvidenceBatch;
import org.predictiveedge.decision.domain.ShadowScope;

/** Captures and appends one complete, point-in-time evidence batch for the fixed shadow scope. */
public final class ShadowEvidenceBatchService {
    private final ShadowScope configuredScope;
    private final Map<DecisionResourceType, DecisionResourceQuery> resourceQueries;
    private final EvidenceManifestQuery manifestQuery;
    private final ExecutionContextQuery executionContextQuery;
    private final ShadowEvidenceBatchStore batchStore;
    private final Clock clock;
    private final Supplier<String> batchIds;

    public ShadowEvidenceBatchService(
            ShadowScope configuredScope,
            List<DecisionResourceQuery> resourceQueries,
            EvidenceManifestQuery manifestQuery,
            ExecutionContextQuery executionContextQuery,
            ShadowEvidenceBatchStore batchStore,
            Clock clock,
            Supplier<String> batchIds) {
        this.configuredScope = Objects.requireNonNull(configuredScope, "Configured shadow scope is required");
        Objects.requireNonNull(resourceQueries, "Decision resource queries are required");
        EnumMap<DecisionResourceType, DecisionResourceQuery> indexed = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceQuery query : resourceQueries) {
            Objects.requireNonNull(query, "Decision resource query cannot be null");
            DecisionResourceType type = Objects.requireNonNull(query.type(), "Decision resource query type is required");
            if (indexed.put(type, query) != null) {
                throw new IllegalArgumentException("Duplicate decision resource query for " + type);
            }
        }
        if (indexed.size() != DecisionResourceType.values().length) {
            throw new IllegalArgumentException("Exactly one query for every decision resource type is required");
        }
        this.resourceQueries = Map.copyOf(indexed);
        this.manifestQuery = Objects.requireNonNull(manifestQuery, "Evidence manifest query is required");
        this.executionContextQuery = Objects.requireNonNull(executionContextQuery, "Execution context query is required");
        this.batchStore = Objects.requireNonNull(batchStore, "Shadow evidence batch store is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.batchIds = Objects.requireNonNull(batchIds, "Batch id supplier is required");
    }

    public ShadowEvidenceBatch capture(Instant cutoff) {
        Objects.requireNonNull(cutoff, "Evidence cutoff is required");
        Instant capturedAt = clock.instant();
        if (capturedAt.isBefore(cutoff)) {
            throw new IllegalArgumentException("Evidence cutoff cannot be in the future");
        }

        EnumMap<DecisionResourceType, DecisionResource> resources = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            DecisionResourceQuery query = resourceQueries.get(type);
            DecisionResource resource = Objects.requireNonNull(
                    query.findLatest(configuredScope, cutoff), "Decision resource query returned no " + type + " resource");
            if (resource.type() != type) {
                throw new IllegalArgumentException("Decision resource query returned the wrong type for " + type);
            }
            configuredScope.requireMatches(resource.userId(), resource.instrument());
            if (resource.availableAt().isAfter(cutoff)) {
                throw new IllegalArgumentException(type + " resource was not available at the evidence cutoff");
            }
            resources.put(type, resource);
        }

        Map<DecisionResourceType, DecisionResource> immutableResources = Map.copyOf(resources);
        PointInTimeEvidenceManifest manifest = Objects.requireNonNull(
                manifestQuery.create(configuredScope, cutoff, immutableResources),
                "Evidence manifest query returned no manifest");
        if (manifest.knowledgeCutoff().isAfter(cutoff)) {
            throw new IllegalArgumentException("Evidence manifest includes knowledge after the evidence cutoff");
        }
        ExecutionContext executionContext = Objects.requireNonNull(
                executionContextQuery.findLatest(configuredScope, cutoff),
                "Execution context query returned no context");
        if (executionContext.observedAt().isAfter(cutoff)) {
            throw new IllegalArgumentException("Execution context was observed after the evidence cutoff");
        }

        ShadowEvidenceBatch batch = new ShadowEvidenceBatch(required(batchIds.get(), "Batch id"), configuredScope,
                capturedAt, manifest, executionContext, immutableResources);
        if (!batchStore.append(batch)) {
            throw new IllegalStateException("Shadow evidence batch already exists");
        }
        return batch;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
