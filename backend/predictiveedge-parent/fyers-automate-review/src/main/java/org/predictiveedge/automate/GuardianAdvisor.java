package org.predictiveedge.automate;

import java.util.Optional;

/**
 * Placeholder for Trade Guardian's continuous monitoring of an open recommendation.
 *
 * <p>A future implementation will watch market conditions for the position and propose an updated
 * recommendation (for example a tighter stop or an exit). Proposals are advisory: they are stored as a
 * {@link RecommendationStage#GUARDIAN_REVISION} version and may be sent to the trader's Automate webhook, but
 * the trader confirms any order change in FYERS. Guardian never calls a broker write API (ADR-0004).
 */
public interface GuardianAdvisor {
    Optional<RecommendationDraft> reassess(RecommendationReview review);

    /** Default until monitoring logic exists: never proposes a change. */
    GuardianAdvisor NONE = review -> Optional.empty();
}
