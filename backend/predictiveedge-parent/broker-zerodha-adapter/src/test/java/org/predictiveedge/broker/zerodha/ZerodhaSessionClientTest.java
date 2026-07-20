package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ZerodhaSessionClientTest {
    @Test
    void invalidatesTheSpecificKiteSession() {
        CapturingTransport transport = new CapturingTransport(200);

        new ZerodhaSessionClient(transport).invalidate("api key", "access/token");

        assertThat(transport.uri).isEqualTo(URI.create(
                "https://api.kite.trade/session/token?api_key=api+key&access_token=access%2Ftoken"));
        assertThat(transport.headers).containsEntry("X-Kite-Version", "3");
    }

    @Test
    void treatsAnAlreadyInvalidTokenAsAnIdempotentDisconnect() {
        assertThatCode(() -> new ZerodhaSessionClient((uri, headers) -> 403)
                .invalidate("api-key", "expired-token")).doesNotThrowAnyException();
    }

    private static final class CapturingTransport implements ZerodhaSessionTerminationTransport {
        private final int status;
        private URI uri;
        private Map<String, String> headers;

        private CapturingTransport(int status) { this.status = status; }

        @Override
        public int delete(URI uri, Map<String, String> headers) {
            this.uri = uri;
            this.headers = headers;
            return status;
        }
    }
}
