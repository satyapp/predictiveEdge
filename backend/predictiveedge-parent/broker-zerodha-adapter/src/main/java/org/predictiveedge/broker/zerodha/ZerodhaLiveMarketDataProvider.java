package org.predictiveedge.broker.zerodha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;
import org.predictiveedge.broker.spi.LiveMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataStream;

/** Authenticated, read-only Kite ticker provider. */
public final class ZerodhaLiveMarketDataProvider implements LiveMarketDataProvider {
    private static final int MAX_INSTRUMENTS = 3_000;

    private final ZerodhaSessionProvider sessions;
    private final ZerodhaWebSocketConnector connector;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ObjectMapper json;
    private final ZerodhaReconnectPolicy reconnectPolicy;

    public ZerodhaLiveMarketDataProvider(ZerodhaSessionProvider sessions, ZerodhaWebSocketConnector connector,
            ScheduledExecutorService scheduler, Clock clock, ObjectMapper json,
            ZerodhaReconnectPolicy reconnectPolicy) {
        this.sessions = Objects.requireNonNull(sessions); this.connector = Objects.requireNonNull(connector);
        this.scheduler = Objects.requireNonNull(scheduler); this.clock = Objects.requireNonNull(clock);
        this.json = Objects.requireNonNull(json); this.reconnectPolicy = Objects.requireNonNull(reconnectPolicy);
    }

    @Override
    public LiveMarketDataStream connect(BrokerContext context, LiveMarketDataSubscription subscription,
            LiveMarketDataListener listener) {
        Objects.requireNonNull(context); Objects.requireNonNull(subscription); Objects.requireNonNull(listener);
        if (subscription.instruments().size() > MAX_INSTRUMENTS)
            throw new IllegalArgumentException("Zerodha permits at most 3000 instruments per WebSocket connection");
        var stream = new ZerodhaTickerStream(context, subscription, listener, sessions, connector, scheduler,
                clock, json, reconnectPolicy);
        stream.start();
        return stream;
    }

    static URI endpoint(ZerodhaSession session) {
        return URI.create("wss://ws.kite.trade?api_key=" + encode(session.apiKey())
                + "&access_token=" + encode(session.accessToken()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static BrokerFailure connectionFailure(String message, Throwable cause) {
        var failure = new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE, message);
        if (cause != null) failure.initCause(cause);
        return failure;
    }
}
