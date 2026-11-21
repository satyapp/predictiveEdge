package org.predictiveedge.nse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.predictiveedge.chart.application.LiveMarketScreenSource;
import org.predictiveedge.chart.application.MarketScreenCsvParser;
import org.predictiveedge.chart.application.MarketScreenDataCopy;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Official NSE CM Market MCP intake adapter. It emits the same canonical copy as CSV intake. */
final class NseMcpLiveMarketScreenSource implements LiveMarketScreenSource {
    private static final URI NIFTY_500_CONSTITUENTS = URI.create(
            "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv");
    private static final Duration UNIVERSE_TTL = Duration.ofHours(12);
    private static final Map<MarketScreenType, List<String>> TOOL_CANDIDATES = tools();
    private static final Set<String> ELIGIBLE_SERIES = Set.of("EQ");
    private final NseMcpGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;
    private final Supplier<HttpClient> http;
    private volatile Set<String> cachedNifty500 = Set.of();
    private volatile Instant universeFetchedAt = Instant.EPOCH;

    NseMcpLiveMarketScreenSource(NseMcpGateway gateway, ObjectMapper json, Clock clock) {
        this(gateway, json, clock,
                () -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    NseMcpLiveMarketScreenSource(NseMcpGateway gateway, ObjectMapper json, Clock clock,
            Supplier<HttpClient> http) {
        this.gateway = Objects.requireNonNull(gateway);
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
        this.http = Objects.requireNonNull(http);
    }

    @Override
    public List<Capability> capabilities() {
        Set<String> available;
        try { available = availableTools(); }
        catch (RuntimeException failure) {
            return TOOL_CANDIDATES.keySet().stream().map(type -> new Capability(type, false, "",
                    "NSE CM Market MCP is unavailable: " + message(failure))).toList();
        }
        CacheReadiness allStocks = allStocksReadiness(available);
        Map<MarketScreenType, CacheReadiness> liveMovers = liveMoverReadiness(available);
        return TOOL_CANDIDATES.entrySet().stream().map(entry -> {
            String tool = entry.getValue().stream().filter(available::contains).findFirst().orElse("");
            if (entry.getKey() == MarketScreenType.INTRADAY_OPPORTUNITY && !allStocks.available())
                return new Capability(entry.getKey(), false, tool, allStocks.detail());
            CacheReadiness moverReadiness = liveMovers.get(entry.getKey());
            if (moverReadiness != null && !moverReadiness.available())
                return new Capability(entry.getKey(), false, tool, moverReadiness.detail());
            return new Capability(entry.getKey(), !tool.isBlank(), tool, tool.isBlank()
                    ? "The connected NSE CM Market MCP does not advertise this discovery tool"
                    : "Official NSE CM Market MCP");
        }).toList();
    }

    private Map<MarketScreenType, CacheReadiness> liveMoverReadiness(Set<String> availableTools) {
        if (!availableTools.contains("cm_get_data_status")) return Map.of(
                MarketScreenType.TOP_GAINERS, new CacheReadiness(false, "NSE MCP does not advertise gainers cache readiness"),
                MarketScreenType.TOP_LOSERS, new CacheReadiness(false, "NSE MCP does not advertise losers cache readiness"));
        try {
            JsonNode status = payload(gateway.callTool(NseMcpServer.CM_MARKET, "cm_get_data_status", Map.of()));
            JsonNode indices = status.path("indices");
            return Map.of(
                    MarketScreenType.TOP_GAINERS, moverReadiness(indices.path("gainers"), "gainers"),
                    MarketScreenType.TOP_LOSERS, moverReadiness(indices.path("loosers"), "losers"));
        } catch (RuntimeException failure) {
            CacheReadiness unavailable = new CacheReadiness(false,
                    "NSE live-movers readiness check failed: " + message(failure));
            return Map.of(MarketScreenType.TOP_GAINERS, unavailable, MarketScreenType.TOP_LOSERS, unavailable);
        }
    }

    private static CacheReadiness moverReadiness(JsonNode status, String label) {
        if (status.path("available").asBoolean(false))
            return new CacheReadiness(true, "Official NSE " + label + " cache is ready");
        String lastCrawled = optionalText(status, "lastCrawled", "lastCrawl", "updatedAt");
        return new CacheReadiness(false, "NSE " + label + " cache is not populated"
                + (lastCrawled.isBlank() ? "" : " (last crawled: " + lastCrawled + ")"));
    }

    private CacheReadiness allStocksReadiness(Set<String> availableTools) {
        if (!availableTools.contains("cm_get_equity_stocks"))
            return new CacheReadiness(false, "NSE MCP does not advertise the all-equity price-watch tool");
        if (!availableTools.contains("cm_get_allstocks_status"))
            return new CacheReadiness(false, "NSE MCP does not advertise all-stocks cache readiness");
        try {
            JsonNode status = payload(gateway.callTool(NseMcpServer.CM_MARKET,
                    "cm_get_allstocks_status", Map.of()));
            if (status.path("available").asBoolean(false))
                return new CacheReadiness(true, "Official NSE all-stocks cache is ready");
            String lastCrawled = optionalText(status, "lastCrawled", "lastCrawl", "updatedAt");
            return new CacheReadiness(false, "NSE all-stocks cache is not populated"
                    + (lastCrawled.isBlank() ? "" : " (last crawled: " + lastCrawled + ")"));
        } catch (RuntimeException failure) {
            return new CacheReadiness(false, "NSE all-stocks readiness check failed: " + message(failure));
        }
    }

    @Override
    public MarketScreenDataCopy fetch(MarketScreenType screenType, int maximumRows) {
        if (maximumRows < 1 || maximumRows > 20)
            throw new IllegalArgumentException("Maximum rows must be between 1 and 20");
        Set<String> available = availableTools();
        String tool = TOOL_CANDIDATES.getOrDefault(screenType, List.of()).stream()
                .filter(available::contains).findFirst().orElseThrow(() -> new IllegalStateException(
                        "NSE CM Market MCP does not currently expose " + screenType + " discovery"));
        Instant fetchedAt = clock.instant();
        List<JsonNode> rows = screenType == MarketScreenType.INTRADAY_OPPORTUNITY
                ? nifty500Rows(tool) : rows(tool, arguments(tool, maximumRows));
        var accepted = new ArrayList<MarketScreenObservation>();
        var rejected = new ArrayList<MarketScreenCsvParser.RowRejection>();
        boolean providerTimestamp = false;
        int sourceRank = 0;
        for (JsonNode raw : rows) {
            sourceRank++;
            String symbol = text(raw, "symbol", "symbolName", "ticker");
            try {
                JsonNode quote = needsQuote(raw) ? quote(symbol) : json.createObjectNode();
                JsonNode merged = merge(raw, quote);
                providerTimestamp = providerTimestamp || has(merged, "latestTimestamp", "nseTimestamp",
                        "timestamp", "lastUpdated", "lastUpdateTime");
                String series = optionalText(merged, "series", "seriesName");
                if (series.isBlank()) series = "EQ";
                if (!ELIGIBLE_SERIES.contains(series.toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Ineligible NSE series " + series);
                BigDecimal ltp = decimal(merged, "ltp", "lastPrice", "last", "close");
                long volume = whole(merged, "trade_quantity", "tradeQuantity", "volume", "totalTradedVolume");
                BigDecimal turnover = optionalDecimal(merged, "turnover", "value", "totalTradedValue");
                if (turnover == null) turnover = ltp.multiply(BigDecimal.valueOf(volume));
                Instant observedAt = timestamp(merged, fetchedAt);
                accepted.add(new MarketScreenObservation(screenType, "NSE", symbol, series, sourceRank,
                        decimal(merged, "open", "openPrice"), decimal(merged, "high", "dayHigh"),
                        decimal(merged, "low", "dayLow"),
                        decimal(merged, "prev_price", "preClosePrice", "previousClose", "prevClose"), ltp,
                decimal(merged, "perChange", "percChange", "pChange", "percentChange", "changePercent"), volume,
                        turnover, optionalText(merged, "ca", "corporateAction"), observedAt));
            } catch (RuntimeException invalid) {
                rejected.add(new MarketScreenCsvParser.RowRejection(sourceRank, symbol.isBlank() ? "ROW_" + sourceRank : symbol,
                        message(invalid)));
            }
        }
        if (accepted.isEmpty()) throw new IllegalStateException("NSE MCP returned no usable mainboard equity rows");
        Instant observedAt = accepted.stream().map(MarketScreenObservation::observedAt).min(Instant::compareTo)
                .orElse(fetchedAt);
        return new MarketScreenDataCopy(screenType, "NSE_CM_MARKET_MCP:" + tool, observedAt, fetchedAt,
                providerTimestamp ? freshness(observedAt, fetchedAt) : "UNKNOWN", accepted, rejected);
    }

    private List<JsonNode> nifty500Rows(String tool) {
        Set<String> universe = nifty500Universe();
        var result = new LinkedHashMap<String, JsonNode>();
        for (char prefix = 'A'; prefix <= 'Z' && result.size() < universe.size(); prefix++) {
            for (JsonNode row : rows(tool, Map.of("limit", 500, "symbolFilter", String.valueOf(prefix)))) {
                String symbol = optionalText(row, "symbol", "symbolName", "ticker").toUpperCase(Locale.ROOT);
                if (universe.contains(symbol)) result.putIfAbsent(symbol, row);
            }
        }
        for (char prefix = '0'; prefix <= '9' && result.size() < universe.size(); prefix++) {
            for (JsonNode row : rows(tool, Map.of("limit", 500, "symbolFilter", String.valueOf(prefix)))) {
                String symbol = optionalText(row, "symbol", "symbolName", "ticker").toUpperCase(Locale.ROOT);
                if (universe.contains(symbol)) result.putIfAbsent(symbol, row);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("NSE MCP returned no Nifty 500 equity rows");
        return List.copyOf(result.values());
    }

    private List<JsonNode> rows(String tool, Map<String, Object> arguments) {
        JsonNode root = payload(gateway.callTool(NseMcpServer.CM_MARKET, tool, arguments));
        if (root.hasNonNull("error")) throw new IllegalStateException(root.path("error").asText());
        return findRows(root);
    }

    private Set<String> nifty500Universe() {
        Instant now = clock.instant();
        Set<String> current = cachedNifty500;
        if (!current.isEmpty() && universeFetchedAt.plus(UNIVERSE_TTL).isAfter(now)) return current;
        synchronized (this) {
            if (!cachedNifty500.isEmpty() && universeFetchedAt.plus(UNIVERSE_TTL).isAfter(now))
                return cachedNifty500;
            try {
                HttpRequest request = HttpRequest.newBuilder(NIFTY_500_CONSTITUENTS)
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "text/csv,*/*")
                        .header("User-Agent", "Mozilla/5.0 (compatible; PredictiveEdge/0.1; research-only)")
                        .header("Referer", "https://www.niftyindices.com/")
                        .GET().build();
                HttpResponse<String> response = http.get().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2)
                    throw new IllegalStateException("Official Nifty 500 constituent download returned HTTP "
                            + response.statusCode());
                Set<String> parsed = parseNifty500(response.body());
                if (parsed.size() < 450)
                    throw new IllegalStateException("Official Nifty 500 constituent file is incomplete");
                cachedNifty500 = parsed;
                universeFetchedAt = now;
                return parsed;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Nifty 500 constituent download was interrupted", interrupted);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Nifty 500 constituent download failed", failure);
            }
        }
    }

    private static Set<String> parseNifty500(String csv) {
        var symbols = new HashSet<String>();
        String[] lines = csv.replace("\r", "").split("\n");
        if (lines.length < 2) return Set.of();
        List<String> header = csvFields(lines[0]);
        int symbolColumn = -1;
        for (int index = 0; index < header.size(); index++)
            if (header.get(index).trim().equalsIgnoreCase("Symbol")) symbolColumn = index;
        if (symbolColumn < 0) throw new IllegalStateException("Official constituent file has no Symbol column");
        for (int line = 1; line < lines.length; line++) {
            List<String> fields = csvFields(lines[line]);
            if (fields.size() > symbolColumn && !fields.get(symbolColumn).isBlank())
                symbols.add(fields.get(symbolColumn).trim().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(symbols);
    }

    private static List<String> csvFields(String line) {
        var result = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"'); index++;
                } else quoted = !quoted;
            } else if (value == ',' && !quoted) {
                result.add(field.toString()); field.setLength(0);
            } else field.append(value);
        }
        result.add(field.toString());
        return result;
    }

    private JsonNode quote(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is unavailable");
        JsonNode result = payload(gateway.callTool(NseMcpServer.CM_MARKET, "cm_get_stock_quote",
                Map.of("symbol", symbol)));
        if (result.hasNonNull("error")) throw new IllegalStateException(result.path("error").asText());
        List<JsonNode> rows = findRows(result);
        return rows.isEmpty() ? result : rows.getFirst();
    }

    private Set<String> availableTools() {
        return gateway.listTools(NseMcpServer.CM_MARKET).stream().map(McpSchema.Tool::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private JsonNode payload(CallToolResult result) {
        if (Boolean.TRUE.equals(result.isError())) throw new IllegalStateException("NSE MCP tool call failed");
        if (result.structuredContent() != null) return json.valueToTree(result.structuredContent());
        String text = result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text)
                .filter(value -> value != null && !value.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalStateException("NSE MCP returned no JSON payload"));
        try { return json.readTree(text); }
        catch (Exception invalid) { throw new IllegalStateException("NSE MCP returned invalid JSON", invalid); }
    }

    private static List<JsonNode> findRows(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (node.isArray() && node.size() > 0 && node.get(0).isObject()) {
            var rows = new ArrayList<JsonNode>(); node.forEach(rows::add); return rows;
        }
        if (node.isObject()) {
            for (String name : List.of("stocks", "data", "gainers", "losers", "results", "securities")) {
                List<JsonNode> rows = findRows(node.get(name)); if (!rows.isEmpty()) return rows;
            }
            var fields = node.fields();
            while (fields.hasNext()) { List<JsonNode> rows = findRows(fields.next().getValue()); if (!rows.isEmpty()) return rows; }
        }
        return List.of();
    }

    private JsonNode merge(JsonNode primary, JsonNode supplement) {
        var result = json.createObjectNode();
        if (supplement != null && supplement.isObject()) result.setAll((com.fasterxml.jackson.databind.node.ObjectNode) supplement);
        if (primary != null && primary.isObject()) result.setAll((com.fasterxml.jackson.databind.node.ObjectNode) primary);
        return result;
    }

    private static boolean needsQuote(JsonNode row) {
        return !has(row, "open", "openPrice") || !has(row, "high", "dayHigh") || !has(row, "low", "dayLow")
                || !has(row, "prev_price", "preClosePrice", "previousClose", "prevClose");
    }

    private static Map<String, Object> arguments(String tool, int maximumRows) {
        if (tool.equals("nse_get_gainers") || tool.equals("nse_get_losers")) return Map.of("limit", maximumRows);
        if (tool.equals("cm_get_equity_stocks")) return Map.of("limit", 500, "symbolFilter", "");
        return Map.of("limit", maximumRows);
    }

    private static String text(JsonNode node, String... names) {
        String value = optionalText(node, names);
        if (value.isBlank()) throw new IllegalArgumentException(names[0] + " is unavailable");
        return value;
    }

    private static String optionalText(JsonNode node, String... names) {
        for (String name : names) if (node != null && node.hasNonNull(name)) return node.get(name).asText().trim();
        return "";
    }

    private static boolean has(JsonNode node, String... names) {
        for (String name : names) if (node != null && node.hasNonNull(name) && !node.get(name).asText().isBlank()) return true;
        return false;
    }

    private static BigDecimal decimal(JsonNode node, String... names) {
        BigDecimal value = optionalDecimal(node, names);
        if (value == null) throw new IllegalArgumentException(names[0] + " is unavailable");
        return value;
    }

    private static BigDecimal optionalDecimal(JsonNode node, String... names) {
        for (String name : names) if (node != null && node.hasNonNull(name)) {
            try { return new BigDecimal(node.get(name).asText().replace(",", "").replace("%", "").trim()); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " is not numeric"); }
        }
        return null;
    }

    private static long whole(JsonNode node, String... names) {
        BigDecimal value = decimal(node, names);
        try { return value.longValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException(names[0] + " is not a whole number"); }
    }

    private static Instant timestamp(JsonNode node, Instant fallback) {
        String raw = optionalText(node, "latestTimestamp", "nseTimestamp", "timestamp", "lastUpdated", "lastUpdateTime");
        if (raw.isBlank()) return fallback;
        try { return Instant.parse(raw); } catch (RuntimeException ignored) { }
        try { return OffsetDateTime.parse(raw).toInstant(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static String freshness(Instant observedAt, Instant fetchedAt) {
        long seconds = Math.max(0, java.time.Duration.between(observedAt, fetchedAt).toSeconds());
        return seconds <= 180 ? "LIVE" : seconds <= 900 ? "DELAYED" : "STALE";
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private record CacheReadiness(boolean available, String detail) { }

    private static Map<MarketScreenType, List<String>> tools() {
        var result = new EnumMap<MarketScreenType, List<String>>(MarketScreenType.class);
        result.put(MarketScreenType.TOP_GAINERS, List.of("nse_get_gainers"));
        result.put(MarketScreenType.TOP_LOSERS, List.of("nse_get_losers"));
        result.put(MarketScreenType.MOST_ACTIVE_EQUITIES,
                List.of("nse_get_most_active_equities", "cm_get_most_active_equities"));
        result.put(MarketScreenType.PRICE_BAND_HITTERS,
                List.of("nse_get_price_band_hitters", "cm_get_price_band_hitters"));
        result.put(MarketScreenType.VOLUME_GAINERS,
                List.of("nse_get_volume_gainers", "cm_get_volume_gainers"));
        result.put(MarketScreenType.NEW_52_WEEK_HIGH,
                List.of("nse_get_52_week_highs", "cm_get_52_week_highs"));
        result.put(MarketScreenType.NEW_52_WEEK_LOW,
                List.of("nse_get_52_week_lows", "cm_get_52_week_lows"));
        result.put(MarketScreenType.INTRADAY_OPPORTUNITY, List.of("cm_get_equity_stocks"));
        return Map.copyOf(result);
    }
}
