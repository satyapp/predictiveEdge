package org.predictiveedge.broker.domain;

public enum MarketDataStreamState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CLOSED,
    FAILED
}
