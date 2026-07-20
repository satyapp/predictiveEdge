package org.predictiveedge.broker.zerodha;

public final class ZerodhaSession {
    private final String apiKey;
    private final String accessToken;

    public ZerodhaSession(String apiKey, String accessToken) {
        if (apiKey == null || apiKey.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Zerodha API key and access token are required");
        }
        this.apiKey = apiKey;
        this.accessToken = accessToken;
    }

    public String apiKey() { return apiKey; }
    public String accessToken() { return accessToken; }

    @Override
    public String toString() {
        return "ZerodhaSession[credentials=REDACTED]";
    }
}
