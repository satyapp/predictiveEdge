package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerContext;

import com.fasterxml.jackson.databind.ObjectMapper;

class ZerodhaHistoricalDataProviderTest {
    @Test
    void mapsKiteCandlesAndSendsVersionedTokenAuthentication() {
        CapturingTransport transport = new CapturingTransport();
        var provider = new ZerodhaHistoricalDataProvider(
                context -> new ZerodhaSession("api-key", "access-token"), transport, new ObjectMapper());
        var request = new HistoricalDataRequest(
                new Instrument("NSE", "INFY"), "408065", CandleInterval.FIVE_MINUTES,
                Instant.parse("2026-01-01T03:45:00Z"), Instant.parse("2026-01-01T04:00:00Z"), false, true);

        var candles = provider.historicalCandles(
                BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD1234"), request);

        assertThat(transport.uri.getPath()).isEqualTo("/instruments/historical/408065/5minute");
        assertThat(transport.uri.getRawQuery())
                .contains("from=2026-01-01+09%3A15%3A00", "to=2026-01-01+09%3A30%3A00", "oi=1");
        assertThat(transport.headers).containsEntry("X-Kite-Version", "3")
                .containsEntry("Authorization", "token api-key:access-token");
        assertThat(candles).hasSize(2);
        assertThat(candles.getFirst().close()).isEqualByComparingTo("101.50");
        assertThat(candles.getFirst().timestamp()).isEqualTo("2026-01-01T03:45:00Z");
        assertThat(candles.getFirst().openInterest()).isEqualTo(1200L);
    }

    private static final class CapturingTransport implements ZerodhaTransport {
        private URI uri;
        private Map<String, String> headers;

        @Override
        public String get(URI uri, Map<String, String> headers) {
            this.uri = uri;
            this.headers = headers;
            return """
                    {"status":"success","data":{"candles":[
                      ["2026-01-01T09:15:00+0530",100.00,102.00,99.50,101.50,15000,1200],
                      ["2026-01-01T09:20:00+0530",101.50,103.00,101.00,102.75,17000,1250]
                    ]}}
                    """;
        }
    }
}
