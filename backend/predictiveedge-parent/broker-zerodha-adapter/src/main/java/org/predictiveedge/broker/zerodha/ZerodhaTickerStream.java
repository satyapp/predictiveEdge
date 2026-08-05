package org.predictiveedge.broker.zerodha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;
import org.predictiveedge.broker.spi.LiveMarketDataStream;

/** One resilient authenticated ticker connection with subscription replay. */
final class ZerodhaTickerStream implements LiveMarketDataStream {
    private final BrokerContext context;
    private final LiveMarketDataSubscription subscription;
    private final LiveMarketDataListener listener;
    private final ZerodhaSessionProvider sessions;
    private final ZerodhaWebSocketConnector connector;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ObjectMapper json;
    private final ZerodhaReconnectPolicy policy;
    private final ZerodhaTickerPacketDecoder decoder;
    private final AtomicReference<MarketDataStreamState> state =
            new AtomicReference<>(MarketDataStreamState.CONNECTING);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicLong generation = new AtomicLong();
    private final Object callbackLock = new Object();
    private volatile WebSocket socket;
    private volatile Instant lastFrameAt;
    private volatile ScheduledFuture<?> staleMonitor;

    ZerodhaTickerStream(BrokerContext context, LiveMarketDataSubscription subscription,
            LiveMarketDataListener listener, ZerodhaSessionProvider sessions, ZerodhaWebSocketConnector connector,
            ScheduledExecutorService scheduler, Clock clock, ObjectMapper json, ZerodhaReconnectPolicy policy) {
        this.context = context; this.subscription = subscription; this.listener = listener; this.sessions = sessions;
        this.connector = connector; this.scheduler = scheduler; this.clock = clock; this.json = json; this.policy = policy;
        this.decoder = new ZerodhaTickerPacketDecoder(subscription);
    }

    void start() {
        notifyState(MarketDataStreamState.CONNECTING);
        connectAttempt(generation.incrementAndGet());
        long interval = Math.max(1_000, policy.staleAfter().toMillis() / 2);
        staleMonitor = scheduler.scheduleWithFixedDelay(this::checkStaleness, interval, interval, TimeUnit.MILLISECONDS);
    }

    @Override public MarketDataStreamState state() { return state.get(); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        generation.incrementAndGet(); reconnectScheduled.set(false);
        var monitor = staleMonitor;
        if (monitor != null) monitor.cancel(false);
        var current = socket;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown");
        notifyState(MarketDataStreamState.CLOSED);
    }

    private void connectAttempt(long attemptGeneration) {
        if (closed.get() || attemptGeneration != generation.get()) return;
        try {
            var session = sessions.sessionFor(context);
            connector.connect(ZerodhaLiveMarketDataProvider.endpoint(session),
                            new SocketListener(attemptGeneration, session))
                    .whenComplete((connected, failure) -> {
                        if (failure != null) connectionFailed(attemptGeneration, session, failure);
                        else if (closed.get() || attemptGeneration != generation.get())
                            connected.sendClose(WebSocket.NORMAL_CLOSURE, "stale connection");
                    });
        } catch (RuntimeException failure) {
            reconnect(attemptGeneration, failure);
        }
    }

    private void connectionFailed(long listenerGeneration, ZerodhaSession session, Throwable cause) {
        if (isAuthenticationFailure(cause)) authenticationRejected(listenerGeneration, session, cause);
        else reconnect(listenerGeneration, cause);
    }

    private void authenticationRejected(long listenerGeneration, ZerodhaSession session, Throwable cause) {
        if (closed.get() || listenerGeneration != generation.get()) return;
        generation.incrementAndGet(); reconnectScheduled.set(false);
        var current = socket;
        if (current != null) current.abort();
        try { sessions.authenticationFailed(context, session); }
        catch (RuntimeException evictionFailure) { cause.addSuppressed(evictionFailure); }
        notifyState(MarketDataStreamState.FAILED);
        notifyFailure(ZerodhaLiveMarketDataProvider.connectionFailure(
                "Zerodha rejected the market-data session; reconnect the broker account", cause));
    }

    private static boolean isAuthenticationFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof WebSocketHandshakeException handshake
                    && (handshake.getResponse().statusCode() == 401 || handshake.getResponse().statusCode() == 403))
                return true;
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("tokenexception") || normalized.contains("invalid token")
                        || normalized.contains("authentication failed")) return true;
            }
        }
        return false;
    }

    private void connected(long listenerGeneration, WebSocket webSocket) {
        if (closed.get() || listenerGeneration != generation.get()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stale connection"); return;
        }
        socket = webSocket; reconnectScheduled.set(false); lastFrameAt = clock.instant();
        String tokens = subscription.instruments().stream().map(value -> value.providerInstrumentId())
                .collect(java.util.stream.Collectors.joining(","));
        String subscribe = "{\"a\":\"subscribe\",\"v\":[" + tokens + "]}";
        String mode = "{\"a\":\"mode\",\"v\":[\"full\",[" + tokens + "]]}";
        webSocket.sendText(subscribe, true).thenCompose(ignored -> webSocket.sendText(mode, true))
                .whenComplete((ignored, failure) -> {
                    if (failure == null && !closed.get() && listenerGeneration == generation.get()) {
                        reconnectAttempts.set(0);
                        notifyState(MarketDataStreamState.CONNECTED);
                    } else reconnect(listenerGeneration, failure);
                });
        webSocket.request(1);
    }

    private void reconnect(long listenerGeneration, Throwable cause) {
        if (closed.get() || listenerGeneration != generation.get() || !reconnectScheduled.compareAndSet(false, true)) return;
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > policy.maximumAttempts()) {
            notifyState(MarketDataStreamState.FAILED);
            notifyFailure(ZerodhaLiveMarketDataProvider.connectionFailure(
                    "Zerodha market-data stream exhausted reconnect attempts", cause));
            return;
        }
        notifyState(MarketDataStreamState.RECONNECTING);
        long nextGeneration = generation.incrementAndGet();
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connectAttempt(nextGeneration);
        }, policy.delayForAttempt(attempt).toMillis(), TimeUnit.MILLISECONDS);
    }

    private void checkStaleness() {
        if (closed.get() || state.get() != MarketDataStreamState.CONNECTED || lastFrameAt == null) return;
        if (lastFrameAt.plus(policy.staleAfter()).isBefore(clock.instant())) {
            var current = socket;
            if (current != null) current.abort();
            reconnect(generation.get(), new IllegalStateException("Zerodha market-data stream became stale"));
        }
    }

    private void notifyState(MarketDataStreamState next) {
        state.set(next);
        synchronized (callbackLock) { listener.onStateChanged(next); }
    }

    private void notifyFailure(RuntimeException failure) {
        synchronized (callbackLock) { listener.onFailure(failure); }
    }

    private final class SocketListener implements WebSocket.Listener {
        private final long listenerGeneration;
        private final ZerodhaSession session;
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        private final StringBuilder text = new StringBuilder();

        private SocketListener(long listenerGeneration, ZerodhaSession session) {
            this.listenerGeneration = listenerGeneration; this.session = session;
        }

        @Override public void onOpen(WebSocket webSocket) { connected(listenerGeneration, webSocket); }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (!closed.get() && listenerGeneration == generation.get()) {
                if (binary.size() + data.remaining() > policy.maximumFrameBytes()) {
                    webSocket.abort();
                    reconnect(listenerGeneration, new IllegalArgumentException("Zerodha binary frame exceeds limit"));
                } else {
                    var bytes = new byte[data.remaining()]; data.get(bytes); binary.writeBytes(bytes);
                    if (last) {
                        lastFrameAt = clock.instant();
                        try {
                            var ticks = decoder.decode(ByteBuffer.wrap(binary.toByteArray()), lastFrameAt);
                            if (!ticks.isEmpty()) synchronized (callbackLock) { listener.onTicks(ticks); }
                        } catch (RuntimeException failure) {
                            webSocket.abort(); reconnect(listenerGeneration, failure);
                        } finally { binary.reset(); }
                    }
                }
            }
            webSocket.request(1); return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > policy.maximumFrameBytes()) {
                webSocket.abort(); reconnect(listenerGeneration,
                        new IllegalArgumentException("Zerodha text frame exceeds limit"));
                return CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                lastFrameAt = clock.instant();
                try {
                    var root = json.readTree(text.toString());
                    if ("error".equals(root.path("type").asText())) {
                        var failure = new IllegalStateException(root.path("data").asText("Zerodha stream error"));
                        webSocket.abort(); connectionFailed(listenerGeneration, session, failure);
                    }
                } catch (Exception invalid) {
                    webSocket.abort(); reconnect(listenerGeneration, invalid);
                } finally { text.setLength(0); }
            }
            webSocket.request(1); return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            reconnect(listenerGeneration, new IllegalStateException("Zerodha stream closed: " + statusCode));
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket webSocket, Throwable error) { reconnect(listenerGeneration, error); }
    }
}
