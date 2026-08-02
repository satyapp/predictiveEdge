package org.predictiveedge.marketintelligence.domain;

/** A phase in a venue trading session. */
public enum MarketSessionPhase {
    PRE_OPEN,
    OPEN_AUCTION,
    CONTINUOUS,
    HALTED,
    CLOSING_AUCTION,
    CLOSED
}
