package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.predictiveedge.broker.domain.BrokerFailure;

public final class ZerodhaSessionClient {
    private static final String SESSION_URI = "https://api.kite.trade/session/token";
    private final ZerodhaSessionTerminationTransport transport;

    public ZerodhaSessionClient(ZerodhaSessionTerminationTransport transport) {
        this.transport = java.util.Objects.requireNonNull(transport, "Transport is required");
    }

    public void invalidate(String apiKey, String accessToken) {
        if (apiKey == null || apiKey.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("API key and access token are required");
        }
        URI uri = URI.create(SESSION_URI + "?api_key=" + encode(apiKey) + "&access_token=" + encode(accessToken));
        int status = transport.delete(uri, Map.of("X-Kite-Version", "3"));
        if ((status < 200 || status >= 300) && status != 403) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha session invalidation failed with HTTP " + status);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
