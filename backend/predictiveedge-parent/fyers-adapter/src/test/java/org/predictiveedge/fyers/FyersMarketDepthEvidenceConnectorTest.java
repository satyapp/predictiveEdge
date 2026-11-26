package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.EvidenceUnavailableException;

class FyersMarketDepthEvidenceConnectorTest {
    private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");

    @Test
    void fetchesDepthForTheNseEquitySymbolWithTheUsersSession() {
        var transport = new CapturingTransport("""
                {"s":"ok","d":{"NSE:INFY-EQ":{"totalbuyqty":5000,"totalsellqty":4200,
                  "bids":[{"price":1500.5,"volume":100,"ord":3}],
                  "ask":[{"price":1500.75,"volume":80,"ord":2}],
                  "o":1490,"h":1510,"l":1488,"c":1500.5,"ltp":1500.5}},"message":""}
                """);
        var connector = connector(transport);

        Object payload = connector.fetch(EvidenceDomain.MARKET_DEPTH, query());

        assertThat(transport.requests).hasSize(1);
        var sent = transport.requests.getFirst();
        assertThat(sent.uri().getPath()).isEqualTo("/data/depth");
        assertThat(sent.uri().getRawQuery()).contains("symbol=NSE%3AINFY-EQ", "ohlcv_flag=1");
        assertThat(sent.headers().firstValue("Authorization")).contains("APP-100:depth-token");
        var depth = (JsonNode) payload;
        assertThat(depth.path("totalbuyqty").asLong()).isEqualTo(5000L);
        assertThat(depth.path("bids").get(0).path("price").decimalValue()).isEqualByComparingTo("1500.5");
    }

    @Test
    void supportsOnlyMarketDepthAndReportsFyersAsTheSource() {
        var connector = connector(new CapturingTransport("{}"));

        assertThat(connector.source()).isEqualTo(EvidenceSource.FYERS);
        assertThat(connector.supports(EvidenceDomain.MARKET_DEPTH)).isTrue();
        assertThat(connector.supports(EvidenceDomain.MARKET_DATA)).isFalse();
        assertThat(connector.supports(EvidenceDomain.HISTORICAL_AND_PREDICTIVE)).isFalse();
    }

    @Test
    void reportsUnavailableWhenFyersHasNoDepthForTheSymbol() {
        var connector = connector(new CapturingTransport("{\"s\":\"ok\",\"d\":{},\"message\":\"\"}"));

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DEPTH, query()))
                .isInstanceOf(EvidenceUnavailableException.class)
                .hasMessageContaining("NSE:INFY-EQ");
    }

    @Test
    void reportsUnavailableWhenFyersRejectsTheRequest() {
        var connector = connector(new CapturingTransport(
                "{\"s\":\"error\",\"code\":-16,\"message\":\"Invalid token\"}"));

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DEPTH, query()))
                .isInstanceOf(EvidenceUnavailableException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void requiresBrokerContextBecauseTheSessionBelongsToAUser() {
        var connector = connector(new CapturingTransport("{}"));
        var query = new EvidenceQuery("INFY", "depth", NOW, null, null, Map.of());

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DEPTH, query))
                .isInstanceOf(EvidenceUnavailableException.class).hasMessageContaining("brokerContext");
    }

    private static FyersMarketDepthEvidenceConnector connector(CapturingTransport transport) {
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), transport);
        return new FyersMarketDepthEvidenceConnector(user -> "APP-100:depth-token", client);
    }

    private static EvidenceQuery query() {
        return new EvidenceQuery("INFY", "Infosys depth", NOW,
                BrokerContext.withoutCredentials(UUID.randomUUID(), "FY1234"), null, Map.of());
    }

    private static final class CapturingTransport implements FyersTransport {
        private final String body;
        private final List<HttpRequest> requests = new ArrayList<>();

        private CapturingTransport(String body) {
            this.body = body;
        }

        @Override
        public Response send(HttpRequest request) {
            requests.add(request);
            return new Response(200, body);
        }
    }
}
