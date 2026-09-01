package org.predictiveedge.marketintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.marketintelligence.domain.BarFinalityPolicy;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.SessionPhaseWindow;

class MarketIntelligenceTickConsumerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ACCOUNT_ID = "ZD123";
    private static final Instrument INFY = new Instrument("NSE", "INFY");
    private static final Instant OPEN = Instant.parse("2026-08-07T03:45:00Z");
    private static final Instant CLOSE = Instant.parse("2026-08-07T10:00:00Z");

    private final MarketSession session = new MarketSession(
            new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"), OPEN, CLOSE,
            List.of(new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS, OPEN, CLOSE)), "nse-calendar-v1");
    private final List<MarketBarRevision> publications = new ArrayList<>();
    private final List<MarketTickRejection> rejections = new ArrayList<>();
    private final RecordingMetrics metrics = new RecordingMetrics();
    private final MarketIntelligenceTickConsumer consumer = new MarketIntelligenceTickConsumer(
            (instrument, timestamp) -> Optional.of(session),
            (userId, accountId, revision) -> publications.add(revision),
            rejections::add,
            metrics,
            EnumSet.of(BarTimeframe.ONE_MINUTE),
            new BarFinalityPolicy(Duration.ofSeconds(2), "finality-v1"),
            "tick-ohlcv-v1");

    @Test
    void reordersTicksWithinTheLatenessBudgetBeforePublishingTheFinalBar() {
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:10Z", "2026-08-07T03:45:10.100Z", "100", 10),
                tick("2026-08-07T03:45:30Z", "2026-08-07T03:45:30.100Z", "110", 15)));
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:20Z", "2026-08-07T03:45:31Z", "90", 12)));

        assertThat(publications).isEmpty();

        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:46:02Z", "2026-08-07T03:46:02.100Z", "105", 20)));

        assertThat(publications).hasSize(1);
        var bar = publications.getFirst();
        assertThat(bar.finalityState()).isEqualTo(BarFinalityState.FINAL);
        assertThat(bar.revision()).isEqualTo(1);
        assertThat(bar.key().interval().startsAt()).isEqualTo(OPEN);
        assertThat(bar.key().interval().endsAt()).isEqualTo(OPEN.plusSeconds(60));
        assertThat(bar.values().open()).isEqualByComparingTo("100");
        assertThat(bar.values().high()).isEqualByComparingTo("110");
        assertThat(bar.values().low()).isEqualByComparingTo("90");
        assertThat(bar.values().close()).isEqualByComparingTo("110");
        assertThat(bar.values().volume()).isEqualTo(15);
        assertThat(rejections).isEmpty();
        assertThat(metrics.receivedTicks).hasSize(4);
        assertThat(metrics.publishedBars).extracting(MarketBarRevision::finalityState)
                .containsExactly(BarFinalityState.FINAL);
    }

    @Test
    void createsACorrectionWhenATickArrivesAfterBarFinality() {
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:10Z", "2026-08-07T03:45:10.100Z", "100", 10),
                tick("2026-08-07T03:45:30Z", "2026-08-07T03:45:30.100Z", "110", 15),
                tick("2026-08-07T03:46:02Z", "2026-08-07T03:46:02.100Z", "105", 20)));

        var late = tick("2026-08-07T03:45:40Z", "2026-08-07T03:46:03Z", "120", 18);
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(late));

        assertThat(publications).hasSize(2);
        var corrected = publications.getLast();
        assertThat(corrected.finalityState()).isEqualTo(BarFinalityState.CORRECTED);
        assertThat(corrected.revision()).isEqualTo(2);
        assertThat(corrected.correctionReason()).isEqualTo("LATE_OR_OUT_OF_ORDER_TICK");
        assertThat(corrected.values().high()).isEqualByComparingTo("120");
        assertThat(corrected.values().close()).isEqualByComparingTo("120");
        assertThat(corrected.values().volume()).isEqualTo(18);

        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(late));
        assertThat(publications).hasSize(2);
        assertThat(rejections).extracting(MarketTickRejection::reason)
                .containsExactly(MarketTickRejection.Reason.DUPLICATE);
        assertThat(metrics.publishedBars).extracting(MarketBarRevision::finalityState)
                .containsExactly(BarFinalityState.FINAL, BarFinalityState.CORRECTED);
        assertThat(metrics.rejectedTicks).extracting(MarketTickRejection::reason)
                .containsExactly(MarketTickRejection.Reason.DUPLICATE);
    }

    @Test
    void rejectsTicksWithoutAnEffectiveSession() {
        var withoutCalendar = new MarketIntelligenceTickConsumer(
                (instrument, timestamp) -> Optional.empty(),
                (userId, accountId, revision) -> publications.add(revision),
                rejections::add,
                EnumSet.of(BarTimeframe.ONE_MINUTE),
                new BarFinalityPolicy(Duration.ZERO, "finality-v1"), "tick-ohlcv-v1");

        withoutCalendar.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:10Z", "2026-08-07T03:45:10.100Z", "100", 10)));

        assertThat(publications).isEmpty();
        assertThat(rejections).extracting(MarketTickRejection::reason)
                .containsExactly(MarketTickRejection.Reason.SESSION_UNAVAILABLE);
    }

    @Test
    void publishesIndexBarsWithZeroVolume() {
        var nifty = new Instrument("NSE", "NIFTY 50");
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                indexTick(nifty, "2026-08-07T03:45:10Z", "25000"),
                indexTick(nifty, "2026-08-07T03:46:02Z", "25010")));

        assertThat(publications).hasSize(1);
        assertThat(publications.getFirst().values().volume()).isZero();
        assertThat(publications.getFirst().key().subject().type().name()).isEqualTo("INDEX");
    }

    @Test
    void rejectsNegativeVolumeDerivedFromACumulativeVolumeReset() {
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:50Z", "2026-08-07T03:45:50.100Z", "100", 100),
                tick("2026-08-07T03:46:10Z", "2026-08-07T03:46:10.100Z", "101", 90),
                tick("2026-08-07T03:47:02Z", "2026-08-07T03:47:02.100Z", "102", 95)));

        assertThat(rejections).extracting(MarketTickRejection::reason)
                .contains(MarketTickRejection.Reason.INVALID_CUMULATIVE_VOLUME);
        assertThat(publications).noneMatch(revision ->
                revision.key().interval().startsAt().equals(OPEN.plusSeconds(60)));
    }

    @Test
    void finalizesTheLastBarWhenAGovernedWatermarkAdvancesWithoutASyntheticTick() {
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:50Z", "2026-08-07T03:45:50.100Z", "100", 10)));

        consumer.advanceWatermark(USER_ID, ACCOUNT_ID, INFY, session.id(),
                Instant.parse("2026-08-07T03:46:02Z"), Instant.parse("2026-08-07T03:46:02.100Z"));

        assertThat(publications).hasSize(1);
        assertThat(publications.getFirst().values().close()).isEqualByComparingTo("100");
    }

    @Test
    void evictsOnlySessionLedgersOlderThanTheRetentionCutoff() {
        consumer.onTicks(USER_ID, ACCOUNT_ID, List.of(
                tick("2026-08-07T03:45:50Z", "2026-08-07T03:45:50.100Z", "100", 10)));

        assertThat(consumer.evictSessionsEndedBefore(CLOSE)).isZero();
        assertThat(consumer.evictSessionsEndedBefore(CLOSE.plusNanos(1))).isEqualTo(1);
    }

    @Test
    void reportsStreamLifecycleAndFailureWithoutTenantLabels() {
        consumer.onStateChanged(USER_ID, ACCOUNT_ID, org.predictiveedge.broker.domain.MarketDataStreamState.CONNECTED);
        consumer.onFailure(USER_ID, ACCOUNT_ID, new IllegalStateException("socket closed"));

        assertThat(metrics.streamStates)
                .containsExactly(org.predictiveedge.broker.domain.MarketDataStreamState.CONNECTED);
        assertThat(metrics.failures).isEqualTo(1);
    }

    private static EquityMarketTick tick(String exchangeAt, String receivedAt, String price, long volume) {
        var last = new BigDecimal(price);
        var exchange = Instant.parse(exchangeAt);
        return new EquityMarketTick(INFY, "408065", last, 1, last, volume, 100, 100,
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("50"),
                new BigDecimal("99"), MarketDepthLevel.emptyBook(), MarketDepthLevel.emptyBook(),
                exchange, exchange, Instant.parse(receivedAt));
    }

    private static IndexMarketTick indexTick(Instrument instrument, String at, String price) {
        var instant = Instant.parse(at);
        var last = new BigDecimal(price);
        return new IndexMarketTick(instrument, "256265", last, new BigDecimal("24900"),
                new BigDecimal("25100"), new BigDecimal("24800"), new BigDecimal("24950"),
                new BigDecimal("0.20"), instant, instant.plusMillis(100));
    }

    private static final class RecordingMetrics implements MarketIntelligenceMetricsPort {
        private final List<org.predictiveedge.broker.domain.MarketTick> receivedTicks = new ArrayList<>();
        private final List<MarketTickRejection> rejectedTicks = new ArrayList<>();
        private final List<MarketBarRevision> publishedBars = new ArrayList<>();
        private final List<org.predictiveedge.broker.domain.MarketDataStreamState> streamStates = new ArrayList<>();
        private int failures;

        @Override public void tickReceived(org.predictiveedge.broker.domain.MarketTick tick) {
            receivedTicks.add(tick);
        }
        @Override public void tickRejected(MarketTickRejection rejection) { rejectedTicks.add(rejection); }
        @Override public void barPublished(MarketBarRevision revision) { publishedBars.add(revision); }
        @Override public void streamStateChanged(org.predictiveedge.broker.domain.MarketDataStreamState state) {
            streamStates.add(state);
        }
        @Override public void streamFailed() { failures++; }
    }
}
