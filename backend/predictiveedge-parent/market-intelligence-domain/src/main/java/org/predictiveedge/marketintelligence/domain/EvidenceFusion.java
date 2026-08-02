package org.predictiveedge.marketintelligence.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fuses evidence after collapsing correlated claims by dependency key. */
public final class EvidenceFusion {
    private EvidenceFusion() {
    }

    public static Map<EvidenceDimension, DimensionAssessment> fuse(
            List<MarketEvidence> evidence, QualityAssessment quality, FusionPolicy policy) {
        Objects.requireNonNull(evidence, "Market evidence is required");
        Objects.requireNonNull(quality, "Quality assessment is required");
        Objects.requireNonNull(policy, "Fusion policy is required");
        if (evidence.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Evidence cannot contain null");
        var result = new EnumMap<EvidenceDimension, DimensionAssessment>(EvidenceDimension.class);
        for (EvidenceDimension dimension : EvidenceDimension.values()) {
            result.put(dimension, fuseDimension(dimension, evidence.stream()
                    .filter(value -> value.dimension() == dimension).toList(), quality.confidenceCap(), policy));
        }
        return Map.copyOf(result);
    }

    private static DimensionAssessment fuseDimension(EvidenceDimension dimension, List<MarketEvidence> evidence,
            int qualityCap, FusionPolicy policy) {
        var byDependency = new LinkedHashMap<String, List<MarketEvidence>>();
        var comparator = Comparator.comparingInt(MarketEvidence::adjustedStrength)
                .thenComparing(value -> value.contentHash().value());
        for (MarketEvidence value : evidence) {
            byDependency.computeIfAbsent(value.dependencyKey(), ignored -> new ArrayList<>()).add(value);
        }
        var selected = new ArrayList<MarketEvidence>();
        var hashes = new ArrayList<ContentHash>();
        boolean internalConflict = false;
        for (List<MarketEvidence> dependencyGroup : byDependency.values()) {
            var representative = dependencyGroup.stream().max(comparator).orElseThrow();
            selected.add(representative);
            long distinctStates = dependencyGroup.stream().filter(value -> value.state() != EvidenceState.UNKNOWN)
                    .map(MarketEvidence::state).distinct().count();
            if (distinctStates > 1) {
                internalConflict = true;
                dependencyGroup.stream().map(MarketEvidence::contentHash).forEach(hashes::add);
            } else {
                hashes.add(representative.contentHash());
            }
        }
        selected.sort(comparator.reversed());
        var active = selected.stream().filter(value -> value.state() != EvidenceState.UNKNOWN).toList();
        if (active.isEmpty()) {
            return new DimensionAssessment(dimension, EvidenceState.UNKNOWN, 0, false, hashes);
        }
        if (internalConflict) {
            int confidence = Math.min(qualityCap,
                    active.stream().mapToInt(MarketEvidence::adjustedStrength).max().orElse(0));
            return new DimensionAssessment(dimension, EvidenceState.MIXED, confidence, true, hashes);
        }
        var leading = active.getFirst();
        var opposing = active.stream().filter(value -> value.state() != leading.state()).findFirst();
        if (opposing.isPresent()
                && leading.adjustedStrength() - opposing.get().adjustedStrength() <= policy.conflictTolerance()) {
            int confidence = Math.min(qualityCap,
                    100 - Math.abs(leading.adjustedStrength() - opposing.get().adjustedStrength()));
            return new DimensionAssessment(dimension, EvidenceState.MIXED, confidence, true, hashes);
        }
        var aligned = active.stream().filter(value -> value.state() == leading.state()).toList();
        int confidence = aligned.stream().mapToInt(MarketEvidence::adjustedStrength).sum() / aligned.size();
        return new DimensionAssessment(dimension, leading.state(), Math.min(confidence, qualityCap), false, hashes);
    }
}
