package org.predictiveedge.guardian.application;

public final class TradeGuardianFailure extends RuntimeException {
    private final Code code;

    public TradeGuardianFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        RECOMMENDATION_ALREADY_MONITORED,
        MONITORING_CASE_NOT_FOUND,
        CONCURRENT_MODIFICATION
    }
}
