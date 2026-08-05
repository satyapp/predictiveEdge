package org.predictiveedge.broker.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;
import org.predictiveedge.broker.spi.LiveMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataStream;

class UserMarketDataSubscriptionManagerTest {
    private final UUID userId = UUID.randomUUID();
    private final BrokerContext context = BrokerContext.withoutCredentials(userId, "ZD123");
    private final RecordingProvider provider = new RecordingProvider();
    private final RecordingUserListener listener = new RecordingUserListener();
    private final UserMarketDataSubscriptionManager manager = new UserMarketDataSubscriptionManager(provider);

    @Test
    void forwardsEventsWithUserIdentityAndExposesCurrentStatus() {
        var subscription = subscription("NIFTY 50", "256265");

        var initial = manager.subscribe(context, subscription, listener);
        provider.stream(0).emitState(MarketDataStreamState.CONNECTED);
        provider.stream(0).emitTicks(List.of(tick()));
        var failure = new IllegalStateException("feed failed");
        provider.stream(0).emitFailure(failure);

        assertThat(initial.state()).isEqualTo(MarketDataStreamState.CONNECTING);
        assertThat(manager.status(userId).orElseThrow().state()).isEqualTo(MarketDataStreamState.CONNECTED);
        assertThat(listener.states).containsExactly(MarketDataStreamState.CONNECTING, MarketDataStreamState.CONNECTED);
        assertThat(listener.ticks).containsExactly(tick());
        assertThat(listener.failures).containsExactly(failure);
        assertThat(listener.userIds).containsOnly(userId);
        assertThat(listener.accountIds).containsOnly("ZD123");
    }

    @Test
    void keepsIdenticalActiveSubscriptionIdempotentAndReplacesChangedSubscription() {
        var first = subscription("NIFTY 50", "256265");
        var second = subscription("INDIA VIX", "264969");

        manager.subscribe(context, first, listener);
        manager.subscribe(context, first, listener);
        assertThat(provider.streams).hasSize(1);

        manager.subscribe(context, second, listener);

        assertThat(provider.streams).hasSize(2);
        assertThat(provider.stream(0).closed).isTrue();
        assertThat(manager.status(userId).orElseThrow().subscription()).isEqualTo(second);

        provider.stream(0).emitTicks(List.of(tick()));
        assertThat(listener.ticks).isEmpty();
        provider.stream(1).emitTicks(List.of(tick()));
        assertThat(listener.ticks).containsExactly(tick());
    }

    @Test
    void stopsEachUserIdempotentlyAndEmitsOneClosedState() {
        manager.subscribe(context, subscription("NIFTY 50", "256265"), listener);

        assertThat(manager.unsubscribe(userId)).isTrue();
        assertThat(manager.unsubscribe(userId)).isFalse();

        assertThat(provider.stream(0).closed).isTrue();
        assertThat(manager.status(userId)).isEmpty();
        assertThat(listener.states).containsExactly(MarketDataStreamState.CONNECTING, MarketDataStreamState.CLOSED);
    }

    @Test
    void restartsAnIdenticalSubscriptionAfterTerminalFailure() {
        var subscription = subscription("NIFTY 50", "256265");
        manager.subscribe(context, subscription, listener);
        provider.stream(0).emitState(MarketDataStreamState.FAILED);

        manager.subscribe(context, subscription, listener);

        assertThat(provider.streams).hasSize(2);
        assertThat(provider.stream(0).closed).isTrue();
    }

    @Test
    void closesAllStreamsAndRejectsNewSubscriptionsAfterShutdown() {
        manager.subscribe(context, subscription("NIFTY 50", "256265"), listener);
        var other = BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD456");
        manager.subscribe(other, subscription("INDIA VIX", "264969"), listener);

        manager.close();
        manager.close();

        assertThat(provider.streams).allMatch(stream -> stream.closed);
        assertThat(manager.statuses()).isEmpty();
        assertThatThrownBy(() -> manager.subscribe(context,
                subscription("NIFTY 50", "256265"), listener))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void leavesNoSubscriptionWhenProviderConnectionFails() {
        var failing = new UserMarketDataSubscriptionManager((requestedContext, subscription, requestedListener) -> {
            throw new IllegalStateException("cannot connect");
        });

        assertThatThrownBy(() -> failing.subscribe(context,
                subscription("NIFTY 50", "256265"), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot connect");
        assertThat(failing.status(userId)).isEmpty();
    }

    private static LiveMarketDataSubscription subscription(String symbol, String token) {
        return new LiveMarketDataSubscription(List.of(new LiveMarketDataInstrument(
                new Instrument("NSE", symbol), token, MarketDataInstrumentKind.INDEX)), MarketDataDetail.FULL);
    }

    private static IndexMarketTick tick() {
        var at = Instant.parse("2026-08-06T04:00:00Z");
        return new IndexMarketTick(new Instrument("NSE", "NIFTY 50"), "256265",
                new BigDecimal("25000"), new BigDecimal("24900"), new BigDecimal("25100"),
                new BigDecimal("24850"), new BigDecimal("24950"), new BigDecimal("0.20"), at, at);
    }

    private static final class RecordingProvider implements LiveMarketDataProvider {
        private final List<FakeStream> streams = new ArrayList<>();

        @Override
        public LiveMarketDataStream connect(BrokerContext context, LiveMarketDataSubscription subscription,
                LiveMarketDataListener listener) {
            var stream = new FakeStream(listener);
            streams.add(stream);
            listener.onStateChanged(MarketDataStreamState.CONNECTING);
            return stream;
        }

        private FakeStream stream(int index) {
            return streams.get(index);
        }
    }

    private static final class FakeStream implements LiveMarketDataStream {
        private final LiveMarketDataListener listener;
        private MarketDataStreamState state = MarketDataStreamState.CONNECTING;
        private boolean closed;

        private FakeStream(LiveMarketDataListener listener) {
            this.listener = listener;
        }

        @Override public MarketDataStreamState state() { return state; }

        @Override
        public void close() {
            closed = true;
            state = MarketDataStreamState.CLOSED;
            listener.onStateChanged(state);
        }

        private void emitTicks(List<MarketTick> ticks) { listener.onTicks(ticks); }
        private void emitState(MarketDataStreamState next) { state = next; listener.onStateChanged(next); }
        private void emitFailure(RuntimeException failure) { listener.onFailure(failure); }
    }

    private static final class RecordingUserListener implements UserMarketDataListener {
        private final List<UUID> userIds = new ArrayList<>();
        private final List<String> accountIds = new ArrayList<>();
        private final List<MarketTick> ticks = new ArrayList<>();
        private final List<MarketDataStreamState> states = new ArrayList<>();
        private final List<RuntimeException> failures = new ArrayList<>();

        @Override
        public void onTicks(UUID userId, String brokerAccountId, List<MarketTick> values) {
            record(userId, brokerAccountId); ticks.addAll(values);
        }

        @Override
        public void onStateChanged(UUID userId, String brokerAccountId, MarketDataStreamState state) {
            record(userId, brokerAccountId); states.add(state);
        }

        @Override
        public void onFailure(UUID userId, String brokerAccountId, RuntimeException failure) {
            record(userId, brokerAccountId); failures.add(failure);
        }

        private void record(UUID userId, String brokerAccountId) {
            userIds.add(userId); accountIds.add(brokerAccountId);
        }
    }
}
