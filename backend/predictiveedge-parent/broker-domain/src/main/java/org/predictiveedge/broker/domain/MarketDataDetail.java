package org.predictiveedge.broker.domain;

/** Requested live quote detail. FULL is required for causal bar construction. */
public enum MarketDataDetail {
    LAST_PRICE,
    QUOTE,
    FULL
}
