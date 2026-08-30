package org.predictiveedge.marketintelligence.application;

import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketContextKey;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;

/** Point-in-time read boundary for an immutable semantic Market Context. */
@FunctionalInterface
public interface MarketContextQueryPort {
    Optional<MarketContextSnapshot> findLatest(
            UUID userId,
            MarketContextKey key,
            EvaluationCutoff cutoff);
}
