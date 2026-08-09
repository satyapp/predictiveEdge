package org.predictiveedge.marketintelligence.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;

/** Point-in-time read boundary for tenant-owned immutable market-bar revisions. */
public interface MarketBarQueryPort {
    Optional<MarketBarRevision> findLatest(
            UUID userId,
            String brokerAccountId,
            ObservationSubject subject,
            BarTimeframe timeframe,
            EvaluationCutoff cutoff);

    List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults);
}
