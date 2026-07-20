package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ZerodhaLoginFlow {
    private ZerodhaLoginFlow() {}

    public static URI loginUri(String apiKey) {
        return loginUri(apiKey, null);
    }

    public static URI loginUri(String apiKey, String redirectParameters) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Zerodha API key is required");
        }
        String uri = "https://kite.zerodha.com/connect/login?v=3&api_key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        if (redirectParameters != null && !redirectParameters.isBlank()) {
            uri += "&redirect_params=" + URLEncoder.encode(redirectParameters, StandardCharsets.UTF_8);
        }
        return URI.create(uri);
    }

    public static String tokenChecksum(String apiKey, String requestToken, String apiSecret) {
        if (apiKey == null || apiKey.isBlank() || requestToken == null || requestToken.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("API key, request token, and API secret are required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((apiKey + requestToken + apiSecret).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
