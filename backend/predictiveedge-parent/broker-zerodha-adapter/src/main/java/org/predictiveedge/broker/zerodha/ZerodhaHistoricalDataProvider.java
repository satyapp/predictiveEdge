package org.predictiveedge.broker.zerodha;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ZerodhaHistoricalDataProvider implements HistoricalMarketDataProvider {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter QUERY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter KITE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final ZerodhaSessionProvider sessions;
    private final ZerodhaTransport transport;
    private final ObjectMapper json;

    public ZerodhaHistoricalDataProvider(
            ZerodhaSessionProvider sessions, ZerodhaTransport transport, ObjectMapper json) {
        this.sessions = java.util.Objects.requireNonNull(sessions, "Session provider is required");
        this.transport = java.util.Objects.requireNonNull(transport, "Transport is required");
        this.json = java.util.Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    public List<Candle> historicalCandles(BrokerContext context, HistoricalDataRequest request) {
        if (!request.providerInstrumentId().matches("[0-9]+")) {
            throw new IllegalArgumentException("Zerodha instrument token must be numeric");
        }
        ZerodhaSession session = sessions.sessionFor(context);
        URI uri = historicalUri(request);
        String body = transport.get(uri, Map.of(
                "X-Kite-Version", "3",
                "Authorization", "token " + session.apiKey() + ":" + session.accessToken()));
        return parseCandles(body, request);
    }

    private URI historicalUri(HistoricalDataRequest request) {
        String query = "from=" + encode(format(request.from()))
                + "&to=" + encode(format(request.to()))
                + "&continuous=" + (request.continuous() ? "1" : "0")
                + "&oi=" + (request.includeOpenInterest() ? "1" : "0");
        return URI.create("https://api.kite.trade/instruments/historical/"
                + request.providerInstrumentId() + "/" + interval(request.interval()) + "?" + query);
    }

    private List<Candle> parseCandles(String body, HistoricalDataRequest request) {
        try {
            JsonNode root = json.readTree(body);
            if (!"success".equals(root.path("status").asText()) || !root.path("data").path("candles").isArray()) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "Zerodha returned an invalid historical-data response");
            }
            List<Candle> candles = new ArrayList<>();
            for (JsonNode row : root.path("data").path("candles")) {
                if (!row.isArray() || row.size() < 6) {
                    throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                            "Zerodha returned a malformed candle");
                }
                Long oi = row.size() > 6 && !row.get(6).isNull() ? row.get(6).longValue() : null;
                candles.add(new Candle(
                        request.instrument(), parseTimestamp(row.get(0).asText()), decimal(row, 1),
                        decimal(row, 2), decimal(row, 3), decimal(row, 4), row.get(5).longValue(), oi));
            }
            return List.copyOf(candles);
        } catch (BrokerFailure failure) {
            throw failure;
        } catch (Exception invalidResponse) {
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "Zerodha historical data could not be parsed");
        }
    }

    private static BigDecimal decimal(JsonNode row, int index) {
        return row.get(index).decimalValue();
    }

    private static Instant parseTimestamp(String value) {
        return OffsetDateTime.parse(value, KITE_TIMESTAMP).toInstant();
    }

    private static String format(Instant value) {
        return QUERY_TIME.format(value.atZone(MARKET_ZONE));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String interval(CandleInterval interval) {
        return switch (interval) {
            case MINUTE -> "minute";
            case THREE_MINUTES -> "3minute";
            case FIVE_MINUTES -> "5minute";
            case TEN_MINUTES -> "10minute";
            case FIFTEEN_MINUTES -> "15minute";
            case THIRTY_MINUTES -> "30minute";
            case HOUR -> "60minute";
            case DAY -> "day";
        };
    }
}
