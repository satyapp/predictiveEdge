package org.predictiveedge.decision.domain;

/** Advisory outcomes only; none of these actions can reach a broker write operation. */
public enum RecommendationAction {
    BUY,
    SELL,
    WAIT,
    NO_TRADE,
    INSUFFICIENT_EVIDENCE;

    public boolean isDirectional() {
        return this == BUY || this == SELL;
    }
}
