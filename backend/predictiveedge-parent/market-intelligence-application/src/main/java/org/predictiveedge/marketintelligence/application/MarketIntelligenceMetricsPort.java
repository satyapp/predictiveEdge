package org.predictiveedge.marketintelligence.application;

import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;

/** Low-cardinality operational measurements emitted by the canonical tick pipeline. */
public interface MarketIntelligenceMetricsPort {
    void tickReceived(MarketTick tick);

    void tickRejected(MarketTickRejection rejection);

    void barPublished(MarketBarRevision revision);

    void streamStateChanged(MarketDataStreamState state);

    void streamFailed();

    static MarketIntelligenceMetricsPort noop() {
        return NoOpMarketIntelligenceMetrics.INSTANCE;
    }

    enum NoOpMarketIntelligenceMetrics implements MarketIntelligenceMetricsPort {
        INSTANCE;

        @Override public void tickReceived(MarketTick tick) { }
        @Override public void tickRejected(MarketTickRejection rejection) { }
        @Override public void barPublished(MarketBarRevision revision) { }
        @Override public void streamStateChanged(MarketDataStreamState state) { }
        @Override public void streamFailed() { }
    }
}
