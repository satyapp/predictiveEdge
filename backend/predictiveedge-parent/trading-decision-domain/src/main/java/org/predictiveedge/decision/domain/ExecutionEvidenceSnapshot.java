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
        GateDisposition gateDisposition,
        ExecutionContext context,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        List<String> evidenceReferences,
        String evidenceHash) {

    public ExecutionEvidenceSnapshot {
        snapshotId = required(snapshotId, "Execution snapshot id");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(readiness, "Execution readiness is required");
        Objects.requireNonNull(gateDisposition, "Execution gate is required");
        if (gateDisposition == GateDisposition.NOT_APPLICABLE)
            throw new IllegalArgumentException("Execution gate must explicitly pass or veto");
        if (readiness == AssessmentReadiness.READY && context == null)
            throw new IllegalArgumentException("Ready execution evidence requires context");
        if (context == null && gateDisposition != GateDisposition.VETO)
            throw new IllegalArgumentException("Unavailable execution evidence must veto");
        if (context != null && gateDisposition == GateDisposition.PASS
                && (!context.entryFeasible() || !context.exitFeasible()))
            throw new IllegalArgumentException("Execution cannot pass when entry or exit is infeasible");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(availableAt, "Available-at time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (analysisCutoff.isAfter(knowledgeCutoff) || knowledgeCutoff.isAfter(availableAt)
                || !availableAt.isBefore(validUntil)
                || (context != null && (availableAt.isBefore(context.observedAt())
                || !validUntil.equals(context.validUntil())))) {
            throw new IllegalArgumentException("Execution snapshot causal times are inconsistent");
        }
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
        if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blanks");
        }
        evidenceHash = required(evidenceHash, "Evidence hash").toLowerCase();
        if (!evidenceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence hash must be SHA-256");
    }

    public ExecutionEvidenceSnapshot(String snapshotId, UUID userId, InstrumentRef instrument,
            AssessmentReadiness readiness, ExecutionContext context, Instant analysisCutoff,
            Instant knowledgeCutoff, Instant availableAt, List<String> evidenceReferences, String evidenceHash) {
        this(snapshotId, userId, instrument, readiness,
                context.entryFeasible() && context.exitFeasible() ? GateDisposition.PASS : GateDisposition.VETO,
                context, analysisCutoff, knowledgeCutoff, availableAt, context.validUntil(),
                evidenceReferences, evidenceHash);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
