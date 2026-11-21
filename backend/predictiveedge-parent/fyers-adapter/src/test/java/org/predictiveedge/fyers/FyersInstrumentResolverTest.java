package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.spi.BrokerContext;

class FyersInstrumentResolverTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesEquitiesAndIndexesFromDailyFyersMasterAndCachesIt() {
        var requestedPaths = new ArrayList<String>();
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), request -> {
            requestedPaths.add(request.uri().getPath());
            return new FyersTransport.Response(200, """
                    {
                      "NSE:INFY-EQ":{"symTicker":"NSE:INFY-EQ","fyToken":"1010000001594"},
                      "NSE:MODIRUBBER-BE":{"symTicker":"NSE:MODIRUBBER-BE","fyToken":"1010000009999"},
                      "NSE:NIFTY50-INDEX":{"symTicker":"NSE:NIFTY50-INDEX","fyToken":"101000000026000"},
                      "NSE:INFY26NOVFUT":{"symTicker":"NSE:INFY26NOVFUT","fyToken":"ignored"}
                    }
                    """);
        });
        var resolver = new FyersInstrumentResolver(client, CLOCK);

        var first = resolver.resolve(context(), List.of(new Instrument("NSE", "INFY"),
                new Instrument("NSE", "NIFTY50"), new Instrument("NSE", "MODIRUBBER")));
        var second = resolver.resolve(context(), List.of(new Instrument("NSE", "INFY")));

        assertThat(requestedPaths).containsExactly("/sym_details/NSE_CM_sym_master.json");
        assertThat(first).hasSize(3);
        assertThat(first.getFirst().providerInstrumentId()).isEqualTo("NSE:INFY-EQ");
        assertThat(first.getFirst().kind()).isEqualTo(MarketDataInstrumentKind.EQUITY);
        assertThat(first.get(1).kind()).isEqualTo(MarketDataInstrumentKind.INDEX);
        assertThat(first.get(2).providerInstrumentId()).isEqualTo("NSE:MODIRUBBER-BE");
        assertThat(second.getFirst().providerInstrumentId()).isEqualTo("NSE:INFY-EQ");
    }

    @Test
    void failsClosedWhenInstrumentIsAbsentFromMaster() {
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), request ->
                new FyersTransport.Response(200, "{\"NSE:SBIN-EQ\":{\"symTicker\":\"NSE:SBIN-EQ\"}}"));
        var resolver = new FyersInstrumentResolver(client, CLOCK);

        assertThatThrownBy(() -> resolver.resolve(context(), List.of(new Instrument("NSE", "INFY"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FYERS instrument is unavailable");
    }

    private static BrokerContext context() {
        return BrokerContext.withoutCredentials(UUID.randomUUID(), "FY1234");
    }
}
