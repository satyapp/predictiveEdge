package org.predictiveedge.marketintelligence.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.predictiveedge.marketintelligence.application.MarketIntelligenceTickConsumer;
import org.springframework.scheduling.annotation.Scheduled;

/** Bounds live in-memory tick ledgers while preserving a configured late-correction window. */
public final class MarketIntelligenceRetentionReaper {
    private final MarketIntelligenceTickConsumer consumer;
    private final Clock clock;
    private final Duration retention;

    public MarketIntelligenceRetentionReaper(
            MarketIntelligenceTickConsumer consumer, Clock clock, Duration retention) {
        this.consumer = Objects.requireNonNull(consumer, "Market-intelligence consumer is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.retention = Objects.requireNonNull(retention, "Session retention is required");
        if (retention.isNegative()) throw new IllegalArgumentException("Session retention cannot be negative");
    }

    @Scheduled(fixedDelayString = "${predictiveedge.market-intelligence.retention-sweep-milliseconds:60000}")
    public void evictExpiredSessions() {
        consumer.evictSessionsEndedBefore(clock.instant().minus(retention));
    }
}
