package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stored lineage envelope for point-in-time execution feasibility. */
public record ExecutionEvidenceSnapshot(
        String snapshotId,
        UUID userId,
        InstrumentRef instrument,
        AssessmentReadiness readiness,
        ExecutionContext context,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        List<String> evidenceReferences,
        String evidenceHash) {

    public ExecutionEvidenceSnapshot {
        snapshotId = required(snapshotId, "Execution snapshot id");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(readiness, "Execution readiness is required");
        Objects.requireNonNull(context, "Execution context is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(availableAt, "Available-at time is required");
        if (analysisCutoff.isAfter(knowledgeCutoff) || knowledgeCutoff.isAfter(availableAt)
                || availableAt.isBefore(context.observedAt()) || !availableAt.isBefore(context.validUntil())) {
            throw new IllegalArgumentException("Execution snapshot causal times are inconsistent");
        }
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
        if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blanks");
        }
        evidenceHash = required(evidenceHash, "Evidence hash").toLowerCase();
        if (!evidenceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence hash must be SHA-256");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
