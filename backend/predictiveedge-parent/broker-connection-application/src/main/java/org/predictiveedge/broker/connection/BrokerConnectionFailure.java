package org.predictiveedge.broker.connection;

public final class BrokerConnectionFailure extends RuntimeException {
    private final Code code;

    public BrokerConnectionFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }

    public enum Code { NOT_CONFIGURED, NOT_CONNECTED, ALREADY_CONNECTED, INVALID_STATE, CONNECTION_FAILED }
}
