package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable resource envelope. It contains evidence and gates, never a trade direction. */
public record DecisionResource(
        String resourceId,
        DecisionResourceType type,
        UUID userId,
        InstrumentRef instrument,
        AssessmentReadiness readiness,
        GateDisposition gateDisposition,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        String payloadRef,
        String evidenceHash) {

    public DecisionResource {
        resourceId = required(resourceId, "Resource id");
        Objects.requireNonNull(type, "Resource type is required");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(readiness, "Readiness is required");
        Objects.requireNonNull(gateDisposition, "Gate disposition is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(availableAt, "Available-at time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (analysisCutoff.isAfter(knowledgeCutoff) || knowledgeCutoff.isAfter(availableAt)
                || !availableAt.isBefore(validUntil)) {
            throw new IllegalArgumentException("Resource causal times are inconsistent");
        }
        payloadRef = required(payloadRef, "Payload reference");
        evidenceHash = sha256(evidenceHash, "Evidence hash");
    }

    public boolean isUsableAt(Instant cutoff) {
        return readiness == AssessmentReadiness.READY
                && gateDisposition != GateDisposition.VETO
                && !availableAt.isAfter(cutoff)
                && cutoff.isBefore(validUntil);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String sha256(String value, String name) {
        String normalized = required(value, name).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be SHA-256");
        return normalized;
    }
}
