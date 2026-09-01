package org.predictiveedge.broker.zerodha;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.BrokerFundsSegment;
import org.predictiveedge.broker.domain.BrokerHolding;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.domain.BrokerPosition;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerAccountSnapshotProvider;
import org.predictiveedge.broker.spi.BrokerContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-only Kite account evidence. This class exposes no order or position-conversion operation. */
public final class ZerodhaAccountSnapshotProvider implements BrokerAccountSnapshotProvider {
    private static final URI MARGINS = URI.create("https://api.kite.trade/user/margins");
    private static final URI POSITIONS = URI.create("https://api.kite.trade/portfolio/positions");
    private static final URI HOLDINGS = URI.create("https://api.kite.trade/portfolio/holdings");
    private static final BrokerId ZERODHA = new BrokerId("zerodha");

    private final ZerodhaSessionProvider sessions;
    private final ZerodhaTransport transport;
    private final ObjectMapper json;
    private final Clock clock;

    public ZerodhaAccountSnapshotProvider(
            ZerodhaSessionProvider sessions, ZerodhaTransport transport, ObjectMapper json, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "Session provider is required");
        this.transport = Objects.requireNonNull(transport, "Transport is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public BrokerAccountSnapshot accountSnapshot(BrokerContext context) {
        Objects.requireNonNull(context, "Broker context is required");
        Instant observedAt = clock.instant();
        ZerodhaSession session = sessions.sessionFor(context);
        Map<String, String> headers = Map.of(
                "X-Kite-Version", "3",
                "Authorization", "token " + session.apiKey() + ":" + session.accessToken());
        JsonNode marginData = data(transport.get(MARGINS, headers), "funds and margins");
        JsonNode positionData = data(transport.get(POSITIONS, headers), "positions");
        JsonNode holdingData = data(transport.get(HOLDINGS, headers), "holdings");
        return new BrokerAccountSnapshot(ZERODHA, context.brokerAccountId(),
                funds(marginData), positions(requiredArray(positionData, "net")),
                positions(requiredArray(positionData, "day")), holdings(holdingData),
                observedAt, clock.instant());
    }

    private JsonNode data(String body, String resource) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode data = root.path("data");
            if (!"success".equals(root.path("status").asText()) || data.isMissingNode() || data.isNull())
                throw invalid(resource);
            return data;
        } catch (BrokerFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid(resource);
        }
    }

    private static Map<String, BrokerFundsSegment> funds(JsonNode data) {
        if (!data.isObject()) throw invalid("funds and margins");
        Map<String, BrokerFundsSegment> result = new LinkedHashMap<>();
        data.properties().forEach(entry -> {
            JsonNode segment = entry.getValue();
            if (!segment.isObject()) throw invalid("funds segment " + entry.getKey());
            result.put(entry.getKey(), new BrokerFundsSegment(entry.getKey(),
                    requiredBoolean(segment, "enabled"), requiredDecimal(segment, "net"),
                    decimalMap(requiredObject(segment, "available")),
                    decimalMap(requiredObject(segment, "utilised"))));
        });
        if (result.isEmpty()) throw invalid("funds and margins");
        return Map.copyOf(result);
    }

    private static List<BrokerPosition> positions(JsonNode rows) {
        List<BrokerPosition> result = new ArrayList<>();
        rows.forEach(row -> result.add(new BrokerPosition(
                instrument(row), requiredText(row, "instrument_token"), requiredText(row, "product"),
                requiredLong(row, "quantity"), requiredLong(row, "overnight_quantity"),
                requiredDecimal(row, "average_price"), requiredDecimal(row, "close_price"),
                requiredDecimal(row, "last_price"), requiredDecimal(row, "value"),
                requiredDecimal(row, "pnl"), requiredDecimal(row, "m2m"),
                requiredDecimal(row, "unrealised"), requiredDecimal(row, "realised"),
                requiredLong(row, "buy_quantity"), requiredLong(row, "sell_quantity"))));
        return List.copyOf(result);
    }

    private static List<BrokerHolding> holdings(JsonNode rows) {
        if (!rows.isArray()) throw invalid("holdings");
        List<BrokerHolding> result = new ArrayList<>();
        rows.forEach(row -> result.add(new BrokerHolding(
                instrument(row), requiredText(row, "instrument_token"), requiredText(row, "isin"),
                requiredText(row, "product"), requiredLong(row, "quantity"),
                requiredLong(row, "t1_quantity"), requiredLong(row, "authorised_quantity"),
                requiredLong(row, "collateral_quantity"), requiredDecimal(row, "average_price"),
                requiredDecimal(row, "last_price"), requiredDecimal(row, "close_price"),
                requiredDecimal(row, "pnl"), requiredDecimal(row, "day_change"),
                requiredDecimal(row, "day_change_percentage"))));
        return List.copyOf(result);
    }

    private static Instrument instrument(JsonNode node) {
        return new Instrument(requiredText(node, "exchange"), requiredText(node, "tradingsymbol"));
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isObject()) throw invalid(field);
        return value;
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) throw invalid(field);
        return value;
    }

    private static Map<String, BigDecimal> decimalMap(JsonNode object) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (var entry : object.properties()) {
            if (!entry.getValue().isNumber()) throw invalid(entry.getKey());
            result.put(entry.getKey(), entry.getValue().decimalValue());
        }
        return Map.copyOf(result);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if ((!value.isTextual() && !value.isIntegralNumber()) || value.asText().isBlank()) throw invalid(field);
        return value.asText();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber()) throw invalid(field);
        return value.longValue();
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) throw invalid(field);
        return value.decimalValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) throw invalid(field);
        return value.booleanValue();
    }

    private static BrokerFailure invalid(String resource) {
        return new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                "Zerodha returned invalid " + resource + " data");
    }
}
