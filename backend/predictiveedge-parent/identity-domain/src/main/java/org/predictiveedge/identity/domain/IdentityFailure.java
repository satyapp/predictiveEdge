package org.predictiveedge.identity.domain;

public final class IdentityFailure extends RuntimeException {
    private final Code code;

    public IdentityFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_CREDENTIALS,
        ACCOUNT_UNAVAILABLE,
        OTP_INVALID,
        OTP_EXPIRED,
        OTP_ATTEMPTS_EXCEEDED,
        ACCESS_TOKEN_INVALID,
        DEPENDENCY_UNAVAILABLE
    }
}
