package org.predictiveedge.automate;

import java.util.List;

/** Builds the final recommendation from PredictiveEdge's stage one and the trader-entered FIA review. */
public interface ReconciliationEngine {
    Result reconcile(Input input);

    /** Name recorded as the source of the final version, for example "ollama:qwen3:8b". */
    String name();

    record Input(String exchange, String symbol, RecommendationVersion stageOne, RecommendationVersion fiaReview,
            LearningSummary learning, List<RecommendationReview> pastReviews) { }

    record Result(RecommendationDraft draft, String engine) { }
}
