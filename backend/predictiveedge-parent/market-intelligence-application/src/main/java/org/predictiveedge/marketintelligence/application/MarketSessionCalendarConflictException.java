package org.predictiveedge.marketintelligence.application;

/** Raised when two calendar definitions would be equally effective for the same venue instant. */
public final class MarketSessionCalendarConflictException extends RuntimeException {
    public MarketSessionCalendarConflictException(String message) {
        super(message);
    }
}
