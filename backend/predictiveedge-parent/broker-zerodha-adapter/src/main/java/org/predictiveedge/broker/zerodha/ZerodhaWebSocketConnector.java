package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ZerodhaWebSocketConnector {
    CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
}
