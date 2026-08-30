package org.predictiveedge.marketintelligence.infrastructure;

import java.time.Duration;
import java.util.Objects;

import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.marketintelligence.application.MarketIntelligenceMetricsPort;
import org.predictiveedge.marketintelligence.application.MarketTickRejection;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Micrometer adapter with bounded tags suitable for Prometheus aggregation and alerting. */
public final class MicrometerMarketIntelligenceMetrics implements MarketIntelligenceMetricsPort {
    private static final String PREFIX = "predictiveedge.market.intelligence.";
    private final MeterRegistry registry;

    public MicrometerMarketIntelligenceMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Meter registry is required");
    }

    @Override
    public void tickReceived(MarketTick tick) {
        Objects.requireNonNull(tick, "Market tick is required");
        String[] tags = tickTags(tick);
        registry.counter(PREFIX + "ticks.received", tags).increment();
        Timer.builder(PREFIX + "tick.transport.lag")
                .description("Time from exchange timestamp to broker tick receipt")
                .tags(tags)
                .register(registry)
                .record(nonNegative(Duration.between(tick.exchangeTimestamp(), tick.receivedAt())));
    }

    @Override
    public void tickRejected(MarketTickRejection rejection) {
        Objects.requireNonNull(rejection, "Market tick rejection is required");
        String[] tickTags = tickTags(rejection.tick());
        registry.counter(PREFIX + "ticks.rejected",
                "venue", tickTags[1], "subject_type", tickTags[3],
                "reason", rejection.reason().name()).increment();
    }

    @Override
    public void barPublished(MarketBarRevision revision) {
        Objects.requireNonNull(revision, "Market bar revision is required");
        String[] tags = barTags(revision);
        registry.counter(PREFIX + "bars.published", tags).increment();
        Timer.builder(PREFIX + "bar.publication.delay")
                .description("Time from bar interval end to revision availability")
                .tags(tags)
                .register(registry)
                .record(nonNegative(Duration.between(
                        revision.key().interval().endsAt(), revision.availableAt())));
    }

    @Override
    public void streamStateChanged(MarketDataStreamState state) {
        Objects.requireNonNull(state, "Market-data stream state is required");
        registry.counter(PREFIX + "stream.state.changes", "state", state.name()).increment();
    }

    @Override
    public void streamFailed() {
        registry.counter(PREFIX + "stream.failures").increment();
    }

    private static String[] tickTags(MarketTick tick) {
        return new String[] {"venue", tick.instrument().exchange(), "subject_type",
                tick instanceof IndexMarketTick ? "INDEX" : "INSTRUMENT"};
    }

    private static String[] barTags(MarketBarRevision revision) {
        return new String[] {
                "venue", revision.key().sessionId().venue(),
                "subject_type", revision.key().subject().type().name(),
                "timeframe", revision.key().timeframe().name(),
                "finality", revision.finalityState().name()
        };
    }

    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
