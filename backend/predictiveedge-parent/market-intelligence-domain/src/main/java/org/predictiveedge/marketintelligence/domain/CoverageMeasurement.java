package org.predictiveedge.marketintelligence.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Numerator, original denominator and exclusions for one governed coverage scope. */
public record CoverageMeasurement(
        String scope,
        int receivedCount,
        int expectedCount,
        List<CoverageExclusion> exclusions) {

    public CoverageMeasurement {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("Coverage scope is required");
        }
        scope = scope.trim();
        if (expectedCount < 1 || receivedCount < 0) {
            throw new IllegalArgumentException("Coverage counts are invalid");
        }
        exclusions = List.copyOf(Objects.requireNonNull(exclusions, "Coverage exclusions are required")
                .stream().sorted(Comparator.comparing(CoverageExclusion::reason)
                        .thenComparingInt(CoverageExclusion::count)).toList());
        if (exclusions.stream().map(CoverageExclusion::reason).distinct().count() != exclusions.size()) {
            throw new IllegalArgumentException("Coverage exclusion reasons must be unique within a scope");
        }
        int assessable = expectedCount - exclusions.stream().mapToInt(CoverageExclusion::count).sum();
        if (assessable < 1) {
            throw new IllegalArgumentException("Coverage exclusions must leave an assessable denominator");
        }
        if (receivedCount > assessable) {
            throw new IllegalArgumentException("Received coverage cannot exceed the assessable denominator");
        }
    }

    public int excludedCount() {
        return exclusions.stream().mapToInt(CoverageExclusion::count).sum();
    }

    public int assessableCount() {
        return expectedCount - excludedCount();
    }

    /** Coverage percentage expressed as deterministic integer basis points, truncated toward zero. */
    public int coverageBasisPoints() {
        return (int) ((long) receivedCount * 10_000L / assessableCount());
    }
}
