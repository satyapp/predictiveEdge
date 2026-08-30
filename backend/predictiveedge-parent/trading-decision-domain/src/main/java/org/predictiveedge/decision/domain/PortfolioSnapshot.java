package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Point-in-time user portfolio exposure and concentration; it contains no trade direction. */
public record PortfolioSnapshot(
        String snapshotId,
        UUID userId,
        InstrumentRef instrument,
        AssessmentReadiness readiness,
        GateDisposition gateDisposition,
        BigDecimal availableCash,
        BigDecimal grossExposure,
        BigDecimal netExposure,
        BigDecimal instrumentQuantity,
        BigDecimal instrumentMarketValue,
        BigDecimal instrumentWeightPercent,
        BigDecimal largestPositionWeightPercent,
        BigDecimal maximumSinglePositionWeightPercent,
        int openPositionCount,
        String policyVersion,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        List<String> evidenceReferences,
        String evidenceHash) {

    public PortfolioSnapshot {
        snapshotId = required(snapshotId, "Portfolio snapshot id");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(readiness, "Portfolio readiness is required");
        Objects.requireNonNull(gateDisposition, "Portfolio gate is required");
        if (gateDisposition == GateDisposition.NOT_APPLICABLE) {
            throw new IllegalArgumentException("Portfolio gate must explicitly pass or veto");
        }
        nonNegative(availableCash, "Available cash");
        nonNegative(grossExposure, "Gross exposure");
        Objects.requireNonNull(netExposure, "Net exposure is required");
        Objects.requireNonNull(instrumentQuantity, "Instrument quantity is required");
        nonNegative(instrumentMarketValue, "Instrument market value");
        percentage(instrumentWeightPercent, "Instrument weight");
        percentage(largestPositionWeightPercent, "Largest-position weight");
        percentage(maximumSinglePositionWeightPercent, "Maximum single-position weight");
        if (openPositionCount < 0) throw new IllegalArgumentException("Open-position count cannot be negative");
        if (gateDisposition == GateDisposition.PASS
                && (instrumentWeightPercent.compareTo(maximumSinglePositionWeightPercent) > 0
                || largestPositionWeightPercent.compareTo(maximumSinglePositionWeightPercent) > 0)) {
            throw new IllegalArgumentException("Portfolio gate cannot pass while concentration exceeds policy");
        }
        policyVersion = required(policyVersion, "Portfolio policy version");
        causalTimes(analysisCutoff, knowledgeCutoff, availableAt, validUntil);
        evidenceReferences = references(evidenceReferences);
        evidenceHash = hash(evidenceHash);
    }

    private static void percentage(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(name + " cannot exceed 100 percent");
        }
    }

    private static void nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " cannot be negative");
    }

    private static void causalTimes(Instant analysis, Instant knowledge, Instant available, Instant validUntil) {
        Objects.requireNonNull(analysis, "Analysis cutoff is required");
        Objects.requireNonNull(knowledge, "Knowledge cutoff is required");
        Objects.requireNonNull(available, "Available-at time is required");
        Objects.requireNonNull(validUntil, "Valid-until time is required");
        if (analysis.isAfter(knowledge) || knowledge.isAfter(available) || !available.isBefore(validUntil)) {
            throw new IllegalArgumentException("Portfolio snapshot causal times are inconsistent");
        }
    }

    private static List<String> references(List<String> values) {
        Objects.requireNonNull(values, "Evidence references are required");
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Evidence references cannot contain blanks");
        }
        return values.stream().map(String::trim).toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String hash(String value) {
        String normalized = required(value, "Evidence hash").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence hash must be SHA-256");
        return normalized;
    }
}
