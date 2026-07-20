package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ZerodhaLoginClientTest {
    @Test
    void exchangesARequestTokenWithoutSendingTheApiSecret() {
        CapturingTransport transport = new CapturingTransport();
        var client = new ZerodhaLoginClient(transport, new ObjectMapper());

        ZerodhaAccessGrant grant = client.exchangeRequestToken("api-key", "request-token", "api-secret");

        assertThat(transport.uri).isEqualTo(URI.create("https://api.kite.trade/session/token"));
        assertThat(transport.headers).containsEntry("X-Kite-Version", "3");
        assertThat(transport.form).containsEntry("api_key", "api-key")
                .containsEntry("request_token", "request-token")
                .containsEntry("checksum", "d93f7cb933c3518b3a5f87fa0b49ff6bc71de987dc59bc8015b296920b762fd0")
                .doesNotContainValue("api-secret");
        assertThat(grant.userId()).isEqualTo("ZD1234");
        assertThat(grant.accessToken()).isEqualTo("issued-access-token");
        assertThat(grant.toString()).doesNotContain("issued-access-token");
    }

    private static final class CapturingTransport implements ZerodhaTokenExchangeTransport {
        private URI uri;
        private Map<String, String> headers;
        private Map<String, String> form;

        @Override
        public String postForm(URI uri, Map<String, String> headers, Map<String, String> form) {
            this.uri = uri;
            this.headers = headers;
            this.form = form;
            return "{\"status\":\"success\",\"data\":{\"user_id\":\"ZD1234\",\"access_token\":\"issued-access-token\"}}";
        }
    }
}
