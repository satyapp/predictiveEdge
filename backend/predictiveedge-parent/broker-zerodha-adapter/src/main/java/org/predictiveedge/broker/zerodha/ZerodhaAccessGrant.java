package org.predictiveedge.broker.zerodha;

public final class ZerodhaAccessGrant {
    private final String userId;
    private final String accessToken;

    public ZerodhaAccessGrant(String userId, String accessToken) {
        if (userId == null || userId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Zerodha user id and access token are required");
        }
        this.userId = userId;
        this.accessToken = accessToken;
    }

    public String userId() { return userId; }
    public String accessToken() { return accessToken; }

    @Override
    public String toString() {
        return "ZerodhaAccessGrant[userId=" + userId + ", accessToken=REDACTED]";
    }
}
