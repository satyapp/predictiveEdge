package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable, auditable revision of one canonical market bar. */
public record MarketBarRevision(
        MarketBarKey key,
        long revision,
        MarketBarValues values,
        Instant observedThrough,
        BarFinalityState finalityState,
        Instant availableAt,
        String correctionReason,
        ContentHash inputManifestHash,
        String aggregationPolicyVersion,
        String finalityPolicyVersion) {

    public MarketBarRevision {
        Objects.requireNonNull(key, "Market bar key is required");
        if (revision < 1) {
            throw new IllegalArgumentException("Bar revision must be positive");
        }
        Objects.requireNonNull(values, "Market bar values are required");
        Objects.requireNonNull(observedThrough, "Observed-through time is required");
        Objects.requireNonNull(finalityState, "Bar finality state is required");
        Objects.requireNonNull(availableAt, "Bar availability time is required");
        Objects.requireNonNull(inputManifestHash, "Input manifest hash is required");
        aggregationPolicyVersion = requiredVersion(aggregationPolicyVersion, "Aggregation policy");
        finalityPolicyVersion = requiredVersion(finalityPolicyVersion, "Finality policy");
        if (observedThrough.isBefore(key.interval().startsAt())
                || observedThrough.isAfter(key.interval().endsAt())) {
            throw new IllegalArgumentException("Observed-through time must fall within the bar interval");
        }
        if (availableAt.isBefore(observedThrough)) {
            throw new IllegalArgumentException("A bar cannot be available before its observed-through time");
        }
        if (finalityState == BarFinalityState.CORRECTED
                && (correctionReason == null || correctionReason.isBlank())) {
            throw new IllegalArgumentException("A corrected bar requires a correction reason");
        }
        if (finalityState != BarFinalityState.CORRECTED
                && correctionReason != null && !correctionReason.isBlank()) {
            throw new IllegalArgumentException("Only a corrected bar may carry a correction reason");
        }
        correctionReason = correctionReason == null ? null : correctionReason.trim();
    }

    public static MarketBarRevision provisional(
            MarketBarKey key,
            MarketBarValues values,
            Instant observedThrough,
            Instant availableAt,
            ContentHash inputManifestHash,
            String aggregationPolicyVersion,
            String finalityPolicyVersion) {
        return new MarketBarRevision(key, 1, values, observedThrough, BarFinalityState.PROVISIONAL,
                availableAt, null, inputManifestHash, aggregationPolicyVersion, finalityPolicyVersion);
    }

    public MarketBarRevision finalizeAt(BarFinalityPolicy policy, Instant eventTimeWatermark, Instant finalizedAt) {
        Objects.requireNonNull(policy, "Finality policy is required");
        Objects.requireNonNull(finalizedAt, "Finalized-at time is required");
        if (finalityState != BarFinalityState.PROVISIONAL) {
            throw new IllegalStateException("Only a provisional bar can become final");
        }
        if (!policy.version().equals(finalityPolicyVersion)) {
            throw new IllegalArgumentException("Finality policy version does not match the bar");
        }
        if (!policy.canFinalize(key.interval(), eventTimeWatermark)) {
            throw new IllegalStateException("Event-time watermark has not passed the finality threshold");
        }
        if (finalizedAt.isBefore(availableAt) || finalizedAt.isBefore(policy.finalityReadyAt(key.interval()))) {
            throw new IllegalArgumentException("Final availability cannot precede prior availability or readiness");
        }
        return new MarketBarRevision(key, revision + 1, values, observedThrough, BarFinalityState.FINAL,
                finalizedAt, null, inputManifestHash, aggregationPolicyVersion, finalityPolicyVersion);
    }

    public MarketBarRevision correct(
            MarketBarValues correctedValues,
            Instant correctedObservedThrough,
            Instant correctedAt,
            String reason,
            ContentHash correctedManifestHash) {
        if (finalityState != BarFinalityState.FINAL && finalityState != BarFinalityState.CORRECTED) {
            throw new IllegalStateException("Only a final or corrected bar can be corrected");
        }
        if (correctedAt == null || correctedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException("Correction availability cannot precede the current revision");
        }
        return new MarketBarRevision(key, revision + 1, correctedValues, correctedObservedThrough,
                BarFinalityState.CORRECTED, correctedAt, reason, correctedManifestHash,
                aggregationPolicyVersion, finalityPolicyVersion);
    }

    public boolean isEligible(EvaluationCutoff cutoff) {
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        return (finalityState == BarFinalityState.FINAL || finalityState == BarFinalityState.CORRECTED)
                && !key.interval().endsAt().isAfter(cutoff.analysisCutoff())
                && !availableAt.isAfter(cutoff.knowledgeCutoff());
    }

    private static String requiredVersion(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " version is required");
        }
        return value.trim();
    }
}
