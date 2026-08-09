package org.predictiveedge.marketintelligence.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

/** Provides bounded causal market-bar reads and deterministic chronological replay. */
public final class MarketBarQueryService {
    public static final int MAXIMUM_PAGE_SIZE = 1_000;
    private final MarketBarQueryPort bars;

    public MarketBarQueryService(MarketBarQueryPort bars) {
        this.bars = Objects.requireNonNull(bars, "Market-bar query port is required");
    }

    public Optional<MarketBarRevision> latest(LatestQuery query) {
        Objects.requireNonNull(query, "Latest market-bar query is required");
        return bars.findLatest(query.userId(), query.brokerAccountId(), query.subject(), query.timeframe(),
                query.cutoff());
    }

    public ReplayPage replay(MarketBarReplayCriteria criteria, int pageSize) {
        Objects.requireNonNull(criteria, "Market-bar replay criteria are required");
        if (pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE)
            throw new IllegalArgumentException("Replay page size must be between 1 and " + MAXIMUM_PAGE_SIZE);
        List<MarketBarRevision> fetched = bars.replay(criteria, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<MarketBarRevision> page = List.copyOf(fetched.subList(0, Math.min(pageSize, fetched.size())));
        MarketBarReplayCursor next = hasMore ? cursor(page.getLast()) : null;
        return new ReplayPage(page, next);
    }

    private static MarketBarReplayCursor cursor(MarketBarRevision revision) {
        var key = revision.key();
        var session = key.sessionId();
        return new MarketBarReplayCursor(key.interval().startsAt(), session.venue(), session.tradingDate(),
                session.sessionCode());
    }

    public record LatestQuery(
            UUID userId,
            String brokerAccountId,
            ObservationSubject subject,
            BarTimeframe timeframe,
            EvaluationCutoff cutoff) {
        public LatestQuery {
            Objects.requireNonNull(userId, "User id is required");
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            brokerAccountId = brokerAccountId.trim();
            Objects.requireNonNull(subject, "Market-bar subject is required");
            if (subject.type() != ObservationSubjectType.INSTRUMENT
                    && subject.type() != ObservationSubjectType.INDEX)
                throw new IllegalArgumentException("Market-bar subject must be an instrument or index");
            Objects.requireNonNull(timeframe, "Bar timeframe is required");
            Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        }
    }

    public record ReplayPage(List<MarketBarRevision> bars, MarketBarReplayCursor next) {
        public ReplayPage {
            bars = List.copyOf(Objects.requireNonNull(bars, "Replay bars are required"));
        }
    }
}
