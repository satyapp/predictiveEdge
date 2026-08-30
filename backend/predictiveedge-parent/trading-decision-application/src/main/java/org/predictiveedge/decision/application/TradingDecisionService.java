package org.predictiveedge.decision.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.predictiveedge.decision.domain.TraderIntent;
import org.predictiveedge.decision.domain.TradingDecisionEngine;
import org.predictiveedge.decision.domain.TradingRecommendation;

/** Coordinates point-in-time intelligence reads and produces an advisory recommendation. */
@Deprecated(forRemoval = true)
public final class TradingDecisionService {
    private final IntelligenceFeedbackQuery feedbackQuery;
    private final TradingDecisionEngine engine;
    private final Clock clock;
    private final Supplier<String> recommendationIds;

    public TradingDecisionService(IntelligenceFeedbackQuery feedbackQuery, TradingDecisionEngine engine,
            Clock clock, Supplier<String> recommendationIds) {
        this.feedbackQuery = Objects.requireNonNull(feedbackQuery, "Feedback query is required");
        this.engine = Objects.requireNonNull(engine, "Decision engine is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.recommendationIds = Objects.requireNonNull(recommendationIds, "Recommendation id supplier is required");
    }

    public TradingRecommendation recommend(TraderIntent intent) {
        Objects.requireNonNull(intent, "Trader intent is required");
        Instant cutoff = clock.instant();
        return engine.evaluate(Objects.requireNonNull(recommendationIds.get(), "Recommendation id is required"),
                intent, feedbackQuery.latestFor(intent, cutoff), cutoff);
    }
}
