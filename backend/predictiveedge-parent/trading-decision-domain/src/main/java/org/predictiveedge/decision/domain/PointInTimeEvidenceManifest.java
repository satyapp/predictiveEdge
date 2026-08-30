package org.predictiveedge.decision.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Causal manifest used to prove that no future evidence entered an AI decision. */
public record PointInTimeEvidenceManifest(
        String manifestId,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        String sourceVersion,
        String featureVersion,
        String adjustmentVersion,
        String instrumentIdentityVersion,
        AssessmentReadiness completeness,
        AssessmentReadiness freshness,
        boolean leakageCheckPassed,
        List<String> evidenceReferences,
        String manifestHash) {

    public PointInTimeEvidenceManifest {
        manifestId = required(manifestId, "Manifest id");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        if (analysisCutoff.isAfter(knowledgeCutoff)) {
            throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
        }
        sourceVersion = required(sourceVersion, "Source version");
        featureVersion = required(featureVersion, "Feature version");
        adjustmentVersion = required(adjustmentVersion, "Adjustment version");
        instrumentIdentityVersion = required(instrumentIdentityVersion, "Instrument identity version");
        Objects.requireNonNull(completeness, "Completeness is required");
        Objects.requireNonNull(freshness, "Freshness is required");
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
        if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blanks");
        }
        manifestHash = required(manifestHash, "Manifest hash").toLowerCase();
        if (!manifestHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Manifest hash must be SHA-256");
    }

    public boolean isReady() {
        return completeness == AssessmentReadiness.READY
                && freshness == AssessmentReadiness.READY
                && leakageCheckPassed;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
