package org.predictiveedge.marketintelligence.domain;

/**
 * Stable, coarse information families required for complete cash-equity market understanding.
 * Detailed payload semantics belong to a versioned {@link ObservationSchemaId}.
 */
public enum ObservationKind {
    TRADE,
    L1_QUOTE,
    ORDER_BOOK_SNAPSHOT,
    ORDER_BOOK_DELTA,
    BAR,
    SERIES_VALUE,
    MARKET_STATUS,
    INSTRUMENT_STATUS,
    UNIVERSE_MEMBERSHIP,
    CORPORATE_ACTION,
    CORPORATE_ANNOUNCEMENT,
    FINANCIAL_STATEMENT,
    EARNINGS_RELEASE,
    OWNERSHIP_SNAPSHOT,
    INSTITUTIONAL_FLOW,
    BULK_DEAL,
    BLOCK_DEAL,
    DELIVERY_STATISTICS,
    SHORT_SELLING_ACTIVITY,
    SECURITIES_LENDING_ACTIVITY,
    NEWS_EVENT,
    MACRO_RELEASE
}
