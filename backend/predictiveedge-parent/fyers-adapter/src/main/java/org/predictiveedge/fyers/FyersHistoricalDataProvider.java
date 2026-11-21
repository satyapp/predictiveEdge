package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

/** FYERS historical candles with provider-limit chunking and duplicate-boundary removal. */
public final class FyersHistoricalDataProvider implements HistoricalMarketDataProvider {
    private static final Pattern SYMBOL = Pattern.compile("(?:NSE|BSE|MCX):[A-Z0-9&_.-]+");
    private static final Duration INTRADAY_WINDOW = Duration.ofDays(100);
    private static final Duration DAILY_WINDOW = Duration.ofDays(366);

    private final Function<UUID, String> authorization;
    private final FyersHttpClient client;

    FyersHistoricalDataProvider(FyersConnectionService connections, FyersHttpClient client) {
        this(connections::authorization, client);
    }

    FyersHistoricalDataProvider(Function<UUID, String> authorization, FyersHttpClient client) {
        this.authorization = authorization;
        this.client = client;
    }

    @Override
    public List<Candle> historicalCandles(BrokerContext context, HistoricalDataRequest request) {
        String providerSymbol = request.providerInstrumentId().trim().toUpperCase();
        if (!SYMBOL.matcher(providerSymbol).matches())
            throw new IllegalArgumentException("FYERS provider instrument id must be an API symbol");

        String auth = authorization.apply(context.userId());
        Duration maximumWindow = request.interval() == CandleInterval.DAY ? DAILY_WINDOW : INTRADAY_WINDOW;
        var candles = new TreeMap<Instant, Candle>();
        Instant cursor = request.from();
        while (cursor.isBefore(request.to())) {
            Instant end = earlier(cursor.plus(maximumWindow), request.to());
            JsonNode response;
            try {
                response = client.history(auth, parameters(providerSymbol, request, cursor, end));
            } catch (IllegalStateException failure) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "FYERS historical-data request failed: " + message(failure));
            }
            parse(response, request).forEach(candle -> candles.put(candle.timestamp(), candle));
            cursor = end;
        }
        return List.copyOf(candles.values());
    }

    private static Map<String, String> parameters(String symbol, HistoricalDataRequest request,
            Instant from, Instant to) {
        var values = new LinkedHashMap<String, String>();
        values.put("symbol", symbol);
        values.put("resolution", resolution(request.interval()));
        values.put("date_format", "0");
        values.put("range_from", Long.toString(from.getEpochSecond()));
        values.put("range_to", Long.toString(to.getEpochSecond()));
        values.put("cont_flag", request.continuous() ? "1" : "0");
        values.put("oi_flag", request.includeOpenInterest() ? "1" : "0");
        return values;
    }

    private static List<Candle> parse(JsonNode root, HistoricalDataRequest request) {
        if (root == null || !root.path("candles").isArray())
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                    "FYERS returned an invalid historical-data response");
        var candles = new ArrayList<Candle>();
        for (JsonNode row : root.path("candles")) {
            if (!row.isArray() || row.size() < 6)
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "FYERS returned a malformed historical candle");
            try {
                Long openInterest = row.size() > 6 && !row.get(6).isNull() ? row.get(6).longValue() : null;
                candles.add(new Candle(request.instrument(), Instant.ofEpochSecond(row.get(0).longValue()),
                        decimal(row, 1), decimal(row, 2), decimal(row, 3), decimal(row, 4),
                        row.get(5).longValue(), openInterest));
            } catch (BrokerFailure failure) {
                throw failure;
            } catch (RuntimeException invalid) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "FYERS historical candle could not be parsed");
            }
        }
        return candles;
    }

    private static BigDecimal decimal(JsonNode row, int index) { return row.get(index).decimalValue(); }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static String resolution(CandleInterval interval) {
        return switch (interval) {
            case MINUTE -> "1";
            case THREE_MINUTES -> "3";
            case FIVE_MINUTES -> "5";
            case TEN_MINUTES -> "10";
            case FIFTEEN_MINUTES -> "15";
            case THIRTY_MINUTES -> "30";
            case HOUR -> "60";
            case DAY -> "D";
        };
    }
}
