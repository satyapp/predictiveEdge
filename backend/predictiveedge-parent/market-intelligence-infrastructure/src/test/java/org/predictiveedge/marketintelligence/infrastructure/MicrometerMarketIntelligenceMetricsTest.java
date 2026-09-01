package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.marketintelligence.application.MarketTickRejection;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerMarketIntelligenceMetricsTest {
    private static final Instant START = Instant.parse("2026-08-07T03:45:00Z");

    @Test
    void recordsLowCardinalityPipelineHealthLagAndQualityMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MicrometerMarketIntelligenceMetrics(registry);
        var instrument = new Instrument("NSE", "INFY");
        var tick = new EquityMarketTick(instrument, "408065", new BigDecimal("100"), 1,
                new BigDecimal("100"), 10, 100, 100, new BigDecimal("100"),
                new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("99"),
                MarketDepthLevel.emptyBook(), MarketDepthLevel.emptyBook(),
                START, START, START.plusMillis(125));
        var rejection = new MarketTickRejection(java.util.UUID.randomUUID(), "ZD123", tick,
                MarketTickRejection.Reason.DUPLICATE, "already seen");
        var key = new MarketBarKey(
                new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:INFY"),
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"),
                BarTimeframe.ONE_MINUTE,
                new BarInterval(START, START.plusSeconds(60), false));
        var revision = new MarketBarRevision(key, 1,
                new MarketBarValues(new BigDecimal("100"), new BigDecimal("110"),
                        new BigDecimal("90"), new BigDecimal("105"), 12),
                START.plusSeconds(50), BarFinalityState.FINAL, START.plusSeconds(62), null,
                new ContentHash("a".repeat(64)), "tick-v1", "finality-v1");

        metrics.tickReceived(tick);
        metrics.tickRejected(rejection);
        metrics.barPublished(revision);
        metrics.streamStateChanged(MarketDataStreamState.RECONNECTING);
        metrics.streamFailed();

        assertThat(registry.get("predictiveedge.market.intelligence.ticks.received")
                .tags("venue", "NSE", "subject_type", "INSTRUMENT").counter().count()).isEqualTo(1);
        assertThat(registry.get("predictiveedge.market.intelligence.tick.transport.lag")
                .tags("venue", "NSE", "subject_type", "INSTRUMENT").timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125);
        assertThat(registry.get("predictiveedge.market.intelligence.ticks.rejected")
                .tags("venue", "NSE", "subject_type", "INSTRUMENT", "reason", "DUPLICATE")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("predictiveedge.market.intelligence.bars.published")
                .tags("venue", "NSE", "subject_type", "INSTRUMENT",
                        "timeframe", "ONE_MINUTE", "finality", "FINAL")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("predictiveedge.market.intelligence.bar.publication.delay")
                .tags("venue", "NSE", "subject_type", "INSTRUMENT",
                        "timeframe", "ONE_MINUTE", "finality", "FINAL")
                .timer().totalTime(TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(registry.get("predictiveedge.market.intelligence.stream.state.changes")
                .tag("state", "RECONNECTING").counter().count()).isEqualTo(1);
        assertThat(registry.get("predictiveedge.market.intelligence.stream.failures")
                .counter().count()).isEqualTo(1);
    }
}
