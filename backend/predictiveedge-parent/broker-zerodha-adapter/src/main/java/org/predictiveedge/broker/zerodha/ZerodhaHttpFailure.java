package org.predictiveedge.broker.zerodha;

/** Internal non-2xx response details. The raw provider body must not be exposed to API clients. */
public final class ZerodhaHttpFailure extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public ZerodhaHttpFailure(int statusCode, String responseBody) {
        super("Zerodha request failed with HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
