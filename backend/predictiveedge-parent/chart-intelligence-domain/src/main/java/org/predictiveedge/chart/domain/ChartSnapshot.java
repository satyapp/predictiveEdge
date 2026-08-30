package org.predictiveedge.chart.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Final chart facts interpreted from governed features; it contains no broker or execution concern. */
public record ChartSnapshot(
        String snapshotId,
        String venue,
        String instrumentId,
        ChartBias higherTimeframeBias,
        ChartBias sessionLocationBias,
        ChartBias momentumBias,
        ChartTrigger trigger,
        boolean trendQualified,
        boolean regimePermitsSetup,
        boolean participationConfirmed,
        int confidence,
        ChartReadiness readiness,
        boolean finalEvidence,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        String inputManifestHash,
        List<String> evidenceReferences) {

    public ChartSnapshot {
        snapshotId = required(snapshotId, "Chart snapshot id");
        venue = required(venue, "Venue").toUpperCase();
        instrumentId = required(instrumentId, "Instrument id").toUpperCase();
        Objects.requireNonNull(higherTimeframeBias, "Higher-timeframe bias is required");
        Objects.requireNonNull(sessionLocationBias, "Session-location bias is required");
        Objects.requireNonNull(momentumBias, "Momentum bias is required");
        Objects.requireNonNull(trigger, "Chart trigger is required");
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Confidence must be 0-100");
        Objects.requireNonNull(readiness, "Chart readiness is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        Objects.requireNonNull(availableAt, "Availability time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (analysisCutoff.isAfter(knowledgeCutoff) || knowledgeCutoff.isAfter(availableAt)
                || !availableAt.isBefore(validUntil)) {
            throw new IllegalArgumentException("Chart snapshot causal times are inconsistent");
        }
        inputManifestHash = required(inputManifestHash, "Input manifest hash").toLowerCase();
        if (!inputManifestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Input manifest hash must be a SHA-256 value");
        }
        Objects.requireNonNull(evidenceReferences, "Evidence references are required");
        if (evidenceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blank values");
        }
        evidenceReferences = evidenceReferences.stream().map(String::trim).toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
