package org.predictiveedge.marketintelligence.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Selects exactly the latest observation revisions that were eligible at both cutoffs. */
public final class PointInTimeObservationSelector {
    private static final Comparator<CanonicalObservationRevision> MANIFEST_ORDER = Comparator
            .comparing(CanonicalObservationRevision::eventTime)
            .thenComparing(observation -> observation.subject().type())
            .thenComparing(observation -> observation.subject().id())
            .thenComparing(observation -> observation.descriptor().kind())
            .thenComparing(observation -> observation.descriptor().schemaId().value())
            .thenComparing(CanonicalObservationRevision::sourceId)
            .thenComparing(CanonicalObservationRevision::sourceEventId)
            .thenComparing(observation -> observation.observationId().toString())
            .thenComparingInt(CanonicalObservationRevision::revision);

    private PointInTimeObservationSelector() {
    }

    public static PointInTimeInputManifest select(
            Collection<CanonicalObservationRevision> revisions,
            EvaluationCutoff cutoff) {
        Objects.requireNonNull(revisions, "Observation revisions are required");
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");

        Map<UUID, List<CanonicalObservationRevision>> histories = new HashMap<>();
        for (CanonicalObservationRevision revision : revisions) {
            if (revision == null) {
                throw new IllegalArgumentException("Observation revisions cannot contain null");
            }
            histories.computeIfAbsent(revision.observationId(), ignored -> new ArrayList<>()).add(revision);
        }

        List<CanonicalObservationRevision> selected = histories.values().stream()
                .map(PointInTimeObservationSelector::validatedHistory)
                .map(history -> latestEligible(history, cutoff))
                .filter(Objects::nonNull)
                .sorted(MANIFEST_ORDER)
                .toList();

        return PointInTimeInputManifest.create(cutoff, selected);
    }

    private static List<CanonicalObservationRevision> validatedHistory(
            List<CanonicalObservationRevision> history) {
        List<CanonicalObservationRevision> sorted = history.stream()
                .sorted(Comparator.comparingInt(CanonicalObservationRevision::revision))
                .toList();
        CanonicalObservationRevision first = sorted.getFirst();
        CanonicalObservationRevision previous = null;
        for (CanonicalObservationRevision current : sorted) {
            if (!sameIdentity(first, current)) {
                throw new IllegalArgumentException("Observation revisions must preserve canonical identity");
            }
            if (previous != null) {
                if (previous.revision() == current.revision()) {
                    throw new IllegalArgumentException("Observation revision numbers must be unique");
                }
                if (!current.usableAt().isAfter(previous.usableAt())) {
                    throw new IllegalArgumentException("Observation revisions must become usable in revision order");
                }
            }
            previous = current;
        }
        return sorted;
    }

    private static CanonicalObservationRevision latestEligible(
            List<CanonicalObservationRevision> history,
            EvaluationCutoff cutoff) {
        CanonicalObservationRevision selected = null;
        for (CanonicalObservationRevision revision : history) {
            if (revision.isEligible(cutoff)) {
                selected = revision;
            }
        }
        return selected;
    }

    private static boolean sameIdentity(
            CanonicalObservationRevision left,
            CanonicalObservationRevision right) {
        return left.descriptor().equals(right.descriptor())
                && left.subject().equals(right.subject())
                && left.sourceId().equals(right.sourceId())
                && left.sourceEventId().equals(right.sourceEventId())
                && left.eventTime().equals(right.eventTime());
    }
}
