package org.predictiveedge.broker.domain;

public final class BrokerFailure extends RuntimeException {
    private final Code code;

    public BrokerFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        UNSUPPORTED_ORDER_TYPE,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_POSITION,
        ORDER_NOT_FOUND,
        CONNECTION_UNAVAILABLE
    }
}
