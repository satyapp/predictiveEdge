package org.predictiveedge.broker.zerodha;

import java.net.URI;
import java.util.Map;

import org.predictiveedge.broker.domain.BrokerFailure;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ZerodhaLoginClient {
    private static final URI TOKEN_URI = URI.create("https://api.kite.trade/session/token");

    private final ZerodhaTokenExchangeTransport transport;
    private final ObjectMapper json;

    public ZerodhaLoginClient(ZerodhaTokenExchangeTransport transport, ObjectMapper json) {
        this.transport = java.util.Objects.requireNonNull(transport, "Transport is required");
        this.json = java.util.Objects.requireNonNull(json, "Object mapper is required");
    }

    public ZerodhaAccessGrant exchangeRequestToken(String apiKey, String requestToken, String apiSecret) {
        String checksum = ZerodhaLoginFlow.tokenChecksum(apiKey, requestToken, apiSecret);
        String body = transport.postForm(TOKEN_URI, Map.of("X-Kite-Version", "3"), Map.of(
                "api_key", apiKey,
                "request_token", requestToken,
                "checksum", checksum));
        try {
            var root = json.readTree(body);
            if (!"success".equals(root.path("status").asText())) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "Zerodha token exchange was rejected");
            }
            return new ZerodhaAccessGrant(
                    root.path("data").path("user_id").asText(),
                    root.path("data").path("access_token").asText());
        } catch (BrokerFailure failure) {
            throw failure;
        } catch (Exception invalidResponse) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha token response could not be parsed");
        }
    }
}
