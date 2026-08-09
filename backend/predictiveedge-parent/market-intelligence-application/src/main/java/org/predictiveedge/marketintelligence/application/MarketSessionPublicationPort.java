package org.predictiveedge.marketintelligence.application;

/** Atomically publishes effective-dated calendar definitions with idempotency and ambiguity protection. */
public interface MarketSessionPublicationPort {
    MarketSessionPublicationResult publish(MarketSessionDefinition definition);
}
