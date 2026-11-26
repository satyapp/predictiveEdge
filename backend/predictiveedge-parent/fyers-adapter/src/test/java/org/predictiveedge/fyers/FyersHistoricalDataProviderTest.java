package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerContext;

class FyersHistoricalDataProviderTest {
    @Test
    void mapsFyersCandlesAndUsesAuthenticatedHistoryParameters() {
        var transport = new CapturingTransport();
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), transport);
        var provider = new FyersHistoricalDataProvider(user -> "APP-100:daily-token", client);
        var request = new HistoricalDataRequest(new Instrument("NSE", "INFY"), "NSE:INFY-EQ",
                CandleInterval.FIVE_MINUTES, Instant.ofEpochSecond(1_700_000_000L),
                Instant.ofEpochSecond(1_700_000_600L), false, true);

        var candles = provider.historicalCandles(
                BrokerContext.withoutCredentials(UUID.randomUUID(), "FY1234"), request);

        assertThat(transport.requests).hasSize(1);
        var sent = transport.requests.getFirst();
        assertThat(sent.uri().getPath()).isEqualTo("/data/history");
        assertThat(sent.uri().getRawQuery()).contains(
                "symbol=NSE%3AINFY-EQ", "resolution=5", "date_format=0",
                // range_to stops at the previous whole minute, so the minute in progress is never requested.
                "range_from=1700000000", "range_to=1700000520", "cont_flag=0", "oi_flag=1");
        assertThat(sent.headers().firstValue("Authorization")).contains("APP-100:daily-token");
        assertThat(candles).hasSize(2);
        assertThat(candles.getFirst().close()).isEqualByComparingTo("101.50");
        assertThat(candles.getFirst().openInterest()).isEqualTo(1200L);
    }

    @Test
    void chunksLongIntradayRangesAndRemovesBoundaryDuplicates() {
        var transport = new CapturingTransport();
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), transport);
        var provider = new FyersHistoricalDataProvider(user -> "APP-100:daily-token", client);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        var request = new HistoricalDataRequest(new Instrument("NSE", "INFY"), "NSE:INFY-EQ",
                CandleInterval.MINUTE, from, from.plus(Duration.ofDays(101)), false, false);

        var candles = provider.historicalCandles(context(), request);

        assertThat(transport.requests).hasSize(2);
        assertThat(candles).hasSize(3);
        assertThat(candles).extracting(value -> value.timestamp().getEpochSecond()).doesNotHaveDuplicates();
    }

    @Test
    void rejectsProviderIdsThatAreNotFyersSymbols() {
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), request -> {
            throw new AssertionError("transport must not be called");
        });
        var provider = new FyersHistoricalDataProvider(user -> "auth", client);
        var request = new HistoricalDataRequest(new Instrument("NSE", "INFY"), "408065",
                CandleInterval.DAY, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), false, false);

        assertThatThrownBy(() -> provider.historicalCandles(context(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FYERS provider instrument id");
    }

    private static BrokerContext context() {
        return BrokerContext.withoutCredentials(UUID.randomUUID(), "FY1234");
    }

    private static final class CapturingTransport implements FyersTransport {
        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public Response send(HttpRequest request) {
            requests.add(request);
            long first = queryValue(request, "range_from");
            long last = queryValue(request, "range_to");
            return new Response(200, """
                    {"s":"ok","candles":[
                      [%d,100.00,102.00,99.50,101.50,15000,1200],
                      [%d,101.50,103.00,101.00,102.75,17000,1250]
                    ]}
                    """.formatted(first, last));
        }

        private static long queryValue(HttpRequest request, String name) {
            for (String value : request.uri().getRawQuery().split("&")) {
                String[] parts = value.split("=", 2);
                if (parts[0].equals(name)) return Long.parseLong(parts[1]);
            }
            throw new AssertionError("Missing query parameter " + name);
        }
    }
}
