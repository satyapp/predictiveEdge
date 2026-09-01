package org.predictiveedge.broker.zerodha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.spi.BrokerContext;
import com.fasterxml.jackson.databind.ObjectMapper;

class ZerodhaAccountSnapshotProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-01T04:00:00Z");
    private static final BrokerContext CONTEXT = BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123");

    @Test
    void readsFundsNetAndDayPositionsAndHoldingsWithoutAnyWriteEndpoint() {
        List<URI> requests = new ArrayList<>();
        ZerodhaTransport transport = (uri, headers) -> {
            requests.add(uri);
            assertThat(headers.get("Authorization")).isEqualTo("token key:access");
            return switch (uri.getPath()) {
                case "/user/margins" -> margins();
                case "/portfolio/positions" -> positions();
                case "/portfolio/holdings" -> holdings();
                default -> throw new AssertionError("Unexpected endpoint " + uri);
            };
        };
        var provider = new ZerodhaAccountSnapshotProvider(context -> new ZerodhaSession("key", "access"),
                transport, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        var snapshot = provider.accountSnapshot(CONTEXT);

        assertThat(requests).extracting(URI::getPath).containsExactly(
                "/user/margins", "/portfolio/positions", "/portfolio/holdings");
        assertThat(snapshot.brokerId().value()).isEqualTo("zerodha");
        assertThat(snapshot.accountId()).isEqualTo("ZD123");
        assertThat(snapshot.funds().get("equity").net()).isEqualByComparingTo("99725.05");
        assertThat(snapshot.funds().get("equity").available().get("live_balance"))
                .isEqualByComparingTo("99725.05");
        assertThat(snapshot.netPositions()).hasSize(1);
        assertThat(snapshot.netPositions().getFirst().instrument().symbol()).isEqualTo("INFY");
        assertThat(snapshot.dayPositions()).isEmpty();
        assertThat(snapshot.holdings()).hasSize(1);
        assertThat(snapshot.holdings().getFirst().isin()).isEqualTo("INE009A01021");
        assertThat(snapshot.observedAt()).isEqualTo(NOW);
        assertThat(snapshot.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void failsClosedWhenARequiredBrokerFactIsMissing() {
        ZerodhaTransport transport = (uri, headers) -> switch (uri.getPath()) {
            case "/user/margins" -> "{\"status\":\"success\",\"data\":{\"equity\":{\"enabled\":true}}}";
            default -> "{\"status\":\"success\",\"data\":[]}";
        };
        var provider = new ZerodhaAccountSnapshotProvider(context -> new ZerodhaSession("key", "access"),
                transport, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> provider.accountSnapshot(CONTEXT))
                .isInstanceOf(BrokerFailure.class)
                .hasMessageContaining("invalid net data");
    }

    private static String margins() {
        return """
                {"status":"success","data":{"equity":{"enabled":true,"net":99725.05,
                "available":{"cash":245431.6,"opening_balance":245431.6,"live_balance":99725.05,
                "collateral":0,"intraday_payin":0,"adhoc_margin":0},
                "utilised":{"debits":145706.55,"exposure":0,"m2m_realised":0,"m2m_unrealised":0}}}}
                """;
    }

    private static String positions() {
        return """
                {"status":"success","data":{"net":[{"tradingsymbol":"INFY","exchange":"NSE",
                "instrument_token":408065,"product":"MIS","quantity":10,"overnight_quantity":0,
                "average_price":1500,"close_price":1490,"last_price":1510,"value":15100,"pnl":100,
                "m2m":100,"unrealised":100,"realised":0,"buy_quantity":10,"sell_quantity":0}],"day":[]}}
                """;
    }

    private static String holdings() {
        return """
                {"status":"success","data":[{"tradingsymbol":"INFY","exchange":"NSE",
                "instrument_token":408065,"isin":"INE009A01021","product":"CNC","quantity":5,
                "t1_quantity":0,"authorised_quantity":0,"collateral_quantity":0,"average_price":1400,
                "last_price":1510,"close_price":1490,"pnl":550,"day_change":20,
                "day_change_percentage":1.342281879}]}
                """;
    }
}
