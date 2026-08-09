package org.predictiveedge.marketintelligence.application;

/** Records ingestion quality outcomes without coupling aggregation to persistence. */
public interface MarketTickRejectionPort {
    void reject(MarketTickRejection rejection);
}
