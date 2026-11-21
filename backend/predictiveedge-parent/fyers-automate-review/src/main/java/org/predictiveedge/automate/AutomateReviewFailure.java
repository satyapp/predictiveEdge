package org.predictiveedge.automate;

/** Expected, user-facing failures of the review loop. */
public final class AutomateReviewFailure extends RuntimeException {
    public enum Code { NOT_FOUND, RATE_LIMITED, INVALID_STATE, DELIVERY_FAILED, AI_UNAVAILABLE }

    private final Code code;

    public AutomateReviewFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
