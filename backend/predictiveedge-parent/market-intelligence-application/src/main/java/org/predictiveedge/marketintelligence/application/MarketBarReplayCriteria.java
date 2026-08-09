package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

/** Tenant-scoped causal range requested for chronological market-bar replay. */
public record MarketBarReplayCriteria(
        UUID userId,
        String brokerAccountId,
        ObservationSubject subject,
        BarTimeframe timeframe,
        Instant fromInclusive,
        Instant toExclusive,
        EvaluationCutoff cutoff,
        MarketBarReplayCursor after) {

    public MarketBarReplayCriteria {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        brokerAccountId = brokerAccountId.trim();
        Objects.requireNonNull(subject, "Market-bar subject is required");
        if (subject.type() != ObservationSubjectType.INSTRUMENT && subject.type() != ObservationSubjectType.INDEX)
            throw new IllegalArgumentException("Market-bar subject must be an instrument or index");
        Objects.requireNonNull(timeframe, "Bar timeframe is required");
        Objects.requireNonNull(fromInclusive, "Replay start is required");
        Objects.requireNonNull(toExclusive, "Replay end is required");
        if (!fromInclusive.isBefore(toExclusive))
            throw new IllegalArgumentException("Replay start must precede replay end");
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        if (after != null && (after.intervalStart().isBefore(fromInclusive)
                || !after.intervalStart().isBefore(toExclusive)))
            throw new IllegalArgumentException("Replay cursor must fall inside the requested range");
    }
}
