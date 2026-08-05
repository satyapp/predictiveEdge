package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;

class ZerodhaLiveMarketDataProviderTest {
    private static final long TOKEN = 408065;
    private static final Instant EXCHANGE_AT = Instant.parse("2026-08-05T10:00:00Z");
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    @AfterEach void shutdown() { scheduler.shutdownNow(); }

    @Test
    void authenticatesSubscribesReassemblesFragmentsAndClosesCleanly() {
        var connector = new FakeConnector(); var sink = new RecordingListener();
        var provider = provider(connector);

        var stream = provider.connect(BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123"),
                subscription(), sink);
        var socket = connector.sockets.getFirst();

        assertThat(connector.uris.getFirst().toString())
                .isEqualTo("wss://ws.kite.trade?api_key=api-key&access_token=access-token");
        assertThat(socket.messages).containsExactly(
                "{\"a\":\"subscribe\",\"v\":[408065]}",
                "{\"a\":\"mode\",\"v\":[\"full\",[408065]]}");
        assertThat(sink.states).containsExactly(MarketDataStreamState.CONNECTING, MarketDataStreamState.CONNECTED);

        var frame = frame(); int split = frame.remaining() / 2;
        connector.listeners.getFirst().onBinary(socket, frame.slice(0, split), false);
        connector.listeners.getFirst().onBinary(socket, frame.slice(split, frame.remaining() - split), true);

        assertThat(sink.ticks).hasSize(1);
        assertThat(sink.ticks.getFirst().lastPrice()).isEqualByComparingTo("2500.50");
        stream.close();
        assertThat(stream.state()).isEqualTo(MarketDataStreamState.CLOSED);
        assertThat(socket.closeSent).isTrue();
    }

    @Test
    void reconnectsAndReplaysSubscriptionAfterUnexpectedClose() throws Exception {
        var connector = new FakeConnector(); var sink = new RecordingListener();
        var stream = provider(connector).connect(BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123"),
                subscription(), sink);

        connector.listeners.getFirst().onClose(connector.sockets.getFirst(), 1006, "network loss");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((connector.sockets.size() < 2 || connector.sockets.get(1).messages.size() < 2)
                && System.nanoTime() < deadline) Thread.onSpinWait();

        assertThat(connector.sockets).hasSize(2);
        assertThat(connector.sockets.get(1).messages).containsExactly(
                "{\"a\":\"subscribe\",\"v\":[408065]}",
                "{\"a\":\"mode\",\"v\":[\"full\",[408065]]}");
        assertThat(sink.states).contains(MarketDataStreamState.RECONNECTING, MarketDataStreamState.CONNECTED);
        stream.close();
    }

    private ZerodhaLiveMarketDataProvider provider(FakeConnector connector) {
        return new ZerodhaLiveMarketDataProvider(context -> new ZerodhaSession("api-key", "access-token"),
                connector, scheduler, Clock.fixed(EXCHANGE_AT.plusMillis(250), ZoneOffset.UTC), new ObjectMapper(),
                new ZerodhaReconnectPolicy(Duration.ofMillis(1), Duration.ofMillis(5), 3,
                        Duration.ofSeconds(30), 1_048_576));
    }

    private static LiveMarketDataSubscription subscription() {
        return new LiveMarketDataSubscription(List.of(new LiveMarketDataInstrument(new Instrument("NSE", "INFY"),
                Long.toString(TOKEN), MarketDataInstrumentKind.EQUITY)), MarketDataDetail.FULL);
    }

    private static ByteBuffer frame() {
        var packet = ByteBuffer.allocate(184).order(ByteOrder.BIG_ENDIAN);
        packet.putInt((int) TOKEN).putInt(250_050).putInt(25).putInt(249_900).putInt(1_250_000)
                .putInt(50_000).putInt(45_000).putInt(248_000).putInt(252_000).putInt(247_000).putInt(246_500)
                .putInt((int) EXCHANGE_AT.minusSeconds(1).getEpochSecond()).putInt(0).putInt(0).putInt(0)
                .putInt((int) EXCHANGE_AT.getEpochSecond());
        packet.position(184); packet.flip();
        return ByteBuffer.allocate(188).order(ByteOrder.BIG_ENDIAN).putShort((short) 1).putShort((short) 184)
                .put(packet).flip();
    }

    private static final class FakeConnector implements ZerodhaWebSocketConnector {
        private final List<URI> uris = new CopyOnWriteArrayList<>();
        private final List<WebSocket.Listener> listeners = new CopyOnWriteArrayList<>();
        private final List<FakeWebSocket> sockets = new CopyOnWriteArrayList<>();

        @Override public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
            var socket = new FakeWebSocket(); uris.add(uri); listeners.add(listener); sockets.add(socket);
            listener.onOpen(socket); return CompletableFuture.completedFuture(socket);
        }
    }

    private static final class RecordingListener implements LiveMarketDataListener {
        private final List<MarketTick> ticks = new CopyOnWriteArrayList<>();
        private final List<MarketDataStreamState> states = new CopyOnWriteArrayList<>();
        private final List<RuntimeException> failures = new CopyOnWriteArrayList<>();
        @Override public void onTicks(List<MarketTick> values) { ticks.addAll(values); }
        @Override public void onStateChanged(MarketDataStreamState value) { states.add(value); }
        @Override public void onFailure(RuntimeException failure) { failures.add(failure); }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> messages = new ArrayList<>(); private boolean closeSent; private boolean aborted;
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            messages.add(data.toString()); return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closeSent = true; return CompletableFuture.completedFuture(this);
        }
        @Override public void request(long n) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return closeSent || aborted; }
        @Override public boolean isInputClosed() { return closeSent || aborted; }
        @Override public void abort() { aborted = true; }
    }
}
