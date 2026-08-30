package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, lineage-bearing feedback from one bounded intelligence module. */
public record IntelligenceFeedback(
        String feedbackId,
        IntelligenceModule module,
        InstrumentRef instrument,
        RecommendationAction proposedAction,
        int confidence,
        AssessmentReadiness readiness,
        GateDisposition gateDisposition,
        boolean finalEvidence,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        String inputManifestHash,
        List<String> reasons,
        List<String> evidenceReferences) {

    public IntelligenceFeedback {
        feedbackId = required(feedbackId, "Feedback id");
        Objects.requireNonNull(module, "Intelligence module is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(proposedAction, "Proposed action is required");
        if (proposedAction == RecommendationAction.NO_TRADE
                || proposedAction == RecommendationAction.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("Module feedback may propose only BUY, SELL, or WAIT");
        }
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence must be 0-100");
        Objects.requireNonNull(readiness, "Readiness is required");
        Objects.requireNonNull(gateDisposition, "Gate disposition is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(availableAt, "Availability time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (analysisCutoff.isAfter(knowledgeCutoff) || knowledgeCutoff.isAfter(availableAt)
                || !availableAt.isBefore(validUntil)) {
            throw new IllegalArgumentException("Feedback causal times are inconsistent");
        }
        inputManifestHash = required(inputManifestHash, "Input manifest hash").toLowerCase();
        if (!inputManifestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Input manifest hash must be a SHA-256 value");
        }
        reasons = immutableStrings(reasons, "Reasons");
        evidenceReferences = immutableStrings(evidenceReferences, "Evidence references");
    }

    public boolean isUsableAt(Instant time) {
        return readiness == AssessmentReadiness.READY && finalEvidence
                && !availableAt.isAfter(time) && time.isBefore(validUntil);
    }

    private static List<String> immutableStrings(List<String> values, String name) {
        Objects.requireNonNull(values, name + " are required");
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " cannot contain blank values");
        }
        return values.stream().map(String::trim).toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
