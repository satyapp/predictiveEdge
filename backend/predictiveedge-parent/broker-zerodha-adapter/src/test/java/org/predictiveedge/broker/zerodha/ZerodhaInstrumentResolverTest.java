package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.spi.BrokerContext;

class ZerodhaInstrumentResolverTest {
    private static final String CSV = """
            instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
            408065,1594,INFY,"INFOSYS, LTD",0,,,0.05,1,EQ,NSE,NSE
            256265,1001,NIFTY 50,NIFTY 50,0,,,0.05,1,,INDICES,NSE
            5720322,22345,NIFTY26AUGFUT,,0,2026-08-27,,0.05,75,FUT,NFO-FUT,NFO
            """;

    private final BrokerContext context = BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123");

    @Test
    void resolvesCashEquitiesAndIndicesAndCachesTheDailyMaster() {
        var transport = new RecordingTransport(CSV);
        var resolver = resolver(transport);

        var first = resolver.resolve(context, List.of(
                new Instrument("NSE", "INFY"), new Instrument("NSE", "NIFTY 50")));
        var second = resolver.resolve(context, List.of(new Instrument("NSE", "INFY")));

        assertThat(first).extracting(value -> value.providerInstrumentId())
                .containsExactly("408065", "256265");
        assertThat(first).extracting(value -> value.kind())
                .containsExactly(MarketDataInstrumentKind.EQUITY, MarketDataInstrumentKind.INDEX);
        assertThat(second).hasSize(1);
        assertThat(transport.calls).hasSize(1);
        assertThat(transport.headers.get("X-Kite-Version")).isEqualTo("3");
        assertThat(transport.headers.get("Authorization")).isEqualTo("token api-key:access-token");
        assertThat(transport.uri.toString()).isEqualTo("https://api.kite.trade/instruments");
    }

    @Test
    void rejectsUnknownDuplicateAndUnsupportedInstruments() {
        var resolver = resolver(new RecordingTransport(CSV));

        assertThatThrownBy(() -> resolver.resolve(context,
                List.of(new Instrument("NFO", "NIFTY26AUGFUT"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable");
        var infy = new Instrument("NSE", "INFY");
        assertThatThrownBy(() -> resolver.resolve(context, List.of(infy, infy)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void failsClosedForMalformedInstrumentMaster() {
        var resolver = resolver(new RecordingTransport("instrument_token,tradingsymbol\n408065,INFY"));

        assertThatThrownBy(() -> resolver.resolve(context, List.of(new Instrument("NSE", "INFY"))))
                .isInstanceOf(BrokerFailure.class)
                .hasMessageContaining("missing instrument_type");
    }

    private static ZerodhaInstrumentResolver resolver(RecordingTransport transport) {
        return new ZerodhaInstrumentResolver(
                ignored -> new ZerodhaSession("api-key", "access-token"), transport,
                Clock.fixed(Instant.parse("2026-08-09T04:00:00Z"), ZoneOffset.UTC));
    }

    private static final class RecordingTransport implements ZerodhaTransport {
        private final String body;
        private final List<String> calls = new ArrayList<>();
        private java.net.URI uri;
        private Map<String, String> headers;

        private RecordingTransport(String body) {
            this.body = body;
        }

        @Override
        public String get(java.net.URI uri, Map<String, String> headers) {
            this.uri = uri;
            this.headers = headers;
            calls.add(uri.toString());
            return body;
        }
    }
}
