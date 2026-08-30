package org.predictiveedge.decision.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Point-in-time user risk limits and utilization; it contains no trade direction. */
public record RiskSnapshot(
        String snapshotId,
        UUID userId,
        InstrumentRef instrument,
        AssessmentReadiness readiness,
        GateDisposition gateDisposition,
        BigDecimal availableCapital,
        BigDecimal maximumRiskPerTrade,
        BigDecimal remainingDailyLossBudget,
        BigDecimal currentOpenRisk,
        BigDecimal maximumOpenRisk,
        BigDecimal maximumPositionValue,
        boolean tradingAllowed,
        String policyVersion,
        Instant analysisCutoff,
        Instant knowledgeCutoff,
        Instant availableAt,
        Instant validUntil,
        List<String> evidenceReferences,
        String evidenceHash) {

    public RiskSnapshot {
        snapshotId = required(snapshotId, "Risk snapshot id");
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(readiness, "Risk readiness is required");
        Objects.requireNonNull(gateDisposition, "Risk gate is required");
        if (gateDisposition == GateDisposition.NOT_APPLICABLE) {
            throw new IllegalArgumentException("Risk gate must explicitly pass or veto");
        }
        nonNegative(availableCapital, "Available capital");
        positive(maximumRiskPerTrade, "Maximum risk per trade");
        nonNegative(remainingDailyLossBudget, "Remaining daily loss budget");
        nonNegative(currentOpenRisk, "Current open risk");
        positive(maximumOpenRisk, "Maximum open risk");
        positive(maximumPositionValue, "Maximum position value");
        if (tradingAllowed != (gateDisposition == GateDisposition.PASS)) {
            throw new IllegalArgumentException("Risk gate must match trading-allowed status");
        }
        if (tradingAllowed && (availableCapital.signum() == 0 || remainingDailyLossBudget.signum() == 0
                || currentOpenRisk.compareTo(maximumOpenRisk) >= 0)) {
            throw new IllegalArgumentException("Risk gate cannot pass without remaining capital and risk capacity");
        }
        policyVersion = required(policyVersion, "Risk policy version");
        causalTimes(analysisCutoff, knowledgeCutoff, availableAt, validUntil);
        evidenceReferences = references(evidenceReferences);
        evidenceHash = hash(evidenceHash);
    }

    private static void positive(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.signum() == 0) throw new IllegalArgumentException(name + " must be positive");
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
            throw new IllegalArgumentException("Risk snapshot causal times are inconsistent");
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
