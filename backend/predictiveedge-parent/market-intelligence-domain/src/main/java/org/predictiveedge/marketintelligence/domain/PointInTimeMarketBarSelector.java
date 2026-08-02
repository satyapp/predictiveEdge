package org.predictiveedge.marketintelligence.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/** Selects the newest final bar revision visible at both causal cutoffs. */
public final class PointInTimeMarketBarSelector {
    private PointInTimeMarketBarSelector() {
    }

    public static Optional<MarketBarRevision> selectLatest(
            Collection<MarketBarRevision> revisions,
            MarketBarKey key,
            EvaluationCutoff cutoff) {
        Objects.requireNonNull(revisions, "Market bar revisions are required");
        Objects.requireNonNull(key, "Market bar key is required");
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");

        var seenRevisions = new HashSet<Long>();
        return revisions.stream()
                .filter(revision -> revision.key().equals(key))
                .peek(revision -> {
                    if (!seenRevisions.add(revision.revision())) {
                        throw new IllegalArgumentException("Duplicate revision for market bar key");
                    }
                })
                .filter(revision -> revision.isEligible(cutoff))
                .max(Comparator.comparingLong(MarketBarRevision::revision));
    }
}
