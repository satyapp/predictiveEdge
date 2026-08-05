package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** JDK WebSocket connector with a bounded handshake timeout. */
public final class JdkZerodhaWebSocketConnector implements ZerodhaWebSocketConnector {
    private final HttpClient client;
    private final Duration connectTimeout;

    public JdkZerodhaWebSocketConnector(HttpClient client, Duration connectTimeout) {
        this.client = Objects.requireNonNull(client, "HTTP client is required");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "Connect timeout is required");
        if (connectTimeout.isZero() || connectTimeout.isNegative())
            throw new IllegalArgumentException("Connect timeout must be positive");
    }

    @Override
    public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
        return client.newWebSocketBuilder().connectTimeout(connectTimeout).buildAsync(uri, listener);
    }
}
