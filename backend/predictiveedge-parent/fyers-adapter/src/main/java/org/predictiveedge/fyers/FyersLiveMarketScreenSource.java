package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.predictiveedge.chart.application.LiveMarketScreenSource;
import org.predictiveedge.chart.application.MarketScreenCsvParser;
import org.predictiveedge.chart.application.MarketScreenDataCopy;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

final class FyersLiveMarketScreenSource implements LiveMarketScreenSource {
    private static final Pattern FYERS_EQUITY = Pattern.compile("NSE:[A-Z0-9&_.-]+-EQ", Pattern.CASE_INSENSITIVE);
    private static final Map<String, List<String>> SETUP_TERMS = Map.ofEntries(
            Map.entry("MOST_TRADED", List.of("most traded", "most active", "turnover")),
            Map.entry("INTRADAY_SURGES", List.of("intraday surge", "top gainers", "gainers")),
            Map.entry("INTRADAY_FALLS", List.of("intraday fall", "top losers", "losers")),
            Map.entry("GAP_UP", List.of("gap up")), Map.entry("GAP_DOWN", List.of("gap down")),
            Map.entry("WEEK_52_HIGH", List.of("52 week high", "52-week high", "new high")),
            Map.entry("WEEK_52_LOW", List.of("52 week low", "52-week low", "new low")),
            Map.entry("UNUSUAL_VOLUME", List.of("unusual volume", "volume shock", "volume gainers")),
            Map.entry("BREAKOUTS", List.of("breakout", "resistance crossed")),
            Map.entry("BREAKDOWNS", List.of("breakdown", "support crossed")));
    private final FyersConnectionService connections;
    private final FyersHttpClient client;
    private final ObjectMapper json;
    private final Clock clock;
    private final LiveMarketScreenSource fallback;
    private volatile JsonNode cachedConfig;
    private volatile Instant configFetchedAt = Instant.EPOCH;

    FyersLiveMarketScreenSource(FyersConnectionService connections, FyersHttpClient client, ObjectMapper json,
            Clock clock, LiveMarketScreenSource fallback) {
        this.connections = connections; this.client = client; this.json = json; this.clock = clock; this.fallback = fallback;
    }
    public List<Capability> capabilities() {
        var values = new ArrayList<Capability>();
        if (fallback != null) values.addAll(fallback.capabilities().stream()
                .filter(value -> value.screenType() != MarketScreenType.INTRADAY_OPPORTUNITY).toList());
        values.add(new Capability(MarketScreenType.INTRADAY_OPPORTUNITY, true, "FYERS_SCREENERS",
                "FYERS OAuth and configuration-driven screeners are enabled"));
        return List.copyOf(values);
    }
    public MarketScreenDataCopy fetch(MarketScreenType screenType, int maximumRows) {
        if (fallback == null) throw new IllegalStateException("No fallback live market source is configured");
        return fallback.fetch(screenType, maximumRows);
    }
    public MarketScreenDataCopy fetch(FetchRequest request) {
        if (request.screenType() != MarketScreenType.INTRADAY_OPPORTUNITY || request.setup().isBlank()) {
            if (fallback == null) throw new IllegalStateException("No fallback live market source is configured");
            return fallback.fetch(request);
        }
        if (request.userId() == null) throw new IllegalArgumentException("User identity is required for FYERS");
        String authorization = connections.authorization(request.userId());
        if ("CUSTOM".equalsIgnoreCase(request.setup())) {
            List<String> selected = java.util.Arrays.stream(request.selectedSymbol().split(","))
                    .map(String::trim).filter(value -> !value.isBlank()).map(FyersLiveMarketScreenSource::providerSymbol)
                    .distinct().limit(request.maximumRows()).toList();
            if (selected.isEmpty()) throw new IllegalArgumentException("At least one custom NSE symbol is required");
            return dataCopy(request, "custom", selected, client.quotes(authorization, String.join(",", selected)));
        }
        JsonNode config = configuration(authorization);
        JsonNode screener = findConfiguredEntry(config.path("data").path("screeners_standard"),
                SETUP_TERMS.getOrDefault(request.setup(), List.of()));
        if (screener == null) throw new IllegalStateException("FYERS configuration does not advertise the selected setup: " + request.setup());
        String screenerId = identifier(screener);
        if (screenerId.isBlank()) throw new IllegalStateException("FYERS did not provide an identifier for " + request.setup());
        JsonNode universe = findConfiguredEntry(config.path("data").path("universes"),
                universeTerms(request.universe()));
        String universeId = universe == null ? "" : identifier(universe);
        if (universeId.isBlank()) throw new IllegalStateException("FYERS configuration does not advertise the selected universe: " + request.universe());
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("screener_id", screenerId);
        if (supports(screener, "universe")) parameters.put("universe", universeId);
        if (supports(screener, "timeframe") && allowedTimeframe(screener, request.timeframe()))
            parameters.put("timeframe", request.timeframe());
        JsonNode screen = client.standardScreener(authorization, parameters);
        List<String> discovered = symbols(screen);
        if (!request.selectedSymbol().isBlank()) {
            String selected = normalize(request.selectedSymbol());
            if (discovered.stream().noneMatch(value -> normalize(value).equals(selected)))
                throw new IllegalStateException("The selected stock is no longer present in the FYERS setup");
            discovered = discovered.stream().filter(value -> normalize(value).equals(selected)).toList();
        }
        if (discovered.isEmpty()) throw new IllegalStateException("FYERS returned no Nifty 500 equities for the selected setup");
        discovered = discovered.stream().limit(request.maximumRows()).toList();
        JsonNode quotes = client.quotes(authorization, String.join(",", discovered));
        return dataCopy(request, screenerId, discovered, quotes);
    }
    private MarketScreenDataCopy dataCopy(FetchRequest request, String screenerId, List<String> symbols, JsonNode root) {
        Instant fetchedAt = clock.instant();
        Map<String, JsonNode> quotes = quoteMap(root);
        var observations = new ArrayList<MarketScreenObservation>();
        var rejected = new ArrayList<MarketScreenCsvParser.RowRejection>();
        int rank = 0;
        for (String providerSymbol : symbols) {
            rank++;
            String symbol = normalize(providerSymbol);
            try {
                JsonNode quote = quotes.get(providerSymbol.toUpperCase(Locale.ROOT));
                if (quote == null) quote = quotes.get("NSE:" + symbol + "-EQ");
                if (quote == null) throw new IllegalArgumentException("FYERS quote is unavailable");
                JsonNode value = quote.has("v") ? quote.path("v") : quote;
                BigDecimal last = decimal(value, "lp", "ltp", "last_price", "close");
                long volume = decimal(value, "volume", "vol_traded_today", "t0_volume").longValue();
                Instant observed = epoch(value.path("tt"), fetchedAt);
                observations.add(new MarketScreenObservation(MarketScreenType.INTRADAY_OPPORTUNITY,
                        "NSE", symbol, "EQ", rank, decimal(value, "open_price", "open"),
                        decimal(value, "high_price", "high"), decimal(value, "low_price", "low"),
                        decimal(value, "prev_close_price", "prev_close"), last,
                        decimal(value, "chp", "percent_change", "per_change"), volume,
                        last.multiply(BigDecimal.valueOf(volume)), "", observed));
            } catch (RuntimeException failure) {
                rejected.add(new MarketScreenCsvParser.RowRejection(rank, symbol, message(failure)));
            }
        }
        if (observations.isEmpty()) throw new IllegalStateException("FYERS returned no usable quote rows");
        Instant observedAt = observations.stream().map(MarketScreenObservation::observedAt)
                .min(Instant::compareTo).orElse(fetchedAt);
        String freshness = java.time.Duration.between(observedAt, fetchedAt).abs().toMinutes() <= 3 ? "LIVE" : "DELAYED";
        return new MarketScreenDataCopy(MarketScreenType.INTRADAY_OPPORTUNITY,
                "FYERS_SCREENERS:" + screenerId + ":" + request.setup(), observedAt, fetchedAt,
                freshness, observations, rejected);
    }
    private JsonNode configuration(String authorization) {
        Instant now = clock.instant(); JsonNode current = cachedConfig;
        if (current != null && configFetchedAt.plusSeconds(3600).isAfter(now)) return current;
        synchronized (this) {
            if (cachedConfig == null || !configFetchedAt.plusSeconds(3600).isAfter(now)) {
                cachedConfig = client.screenersConfig(authorization); configFetchedAt = now;
            }
            return cachedConfig;
        }
    }
    static JsonNode findConfiguredEntry(JsonNode entries, List<String> terms) {
        if (entries == null || !entries.isArray() || terms.isEmpty()) return null;
        for (JsonNode entry : entries) {
            if (!entry.isObject() || identifier(entry).isBlank()) continue;
            String text = String.join(" ", entry.path("name").asText(), entry.path("category").asText(),
                    entry.path("description").asText(), entry.path("conditions").asText())
                    .toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
            boolean match = terms.stream().map(value -> value.toLowerCase(Locale.ROOT).replace('-', ' '))
                    .anyMatch(text::contains);
            if (match) return entry;
        }
        return null;
    }
    private static boolean supports(JsonNode screener, String parameter) {
        JsonNode supported = screener.path("supported_params");
        if (!supported.isArray()) return false;
        for (JsonNode value : supported) if (parameter.equalsIgnoreCase(value.asText())) return true;
        return false;
    }
    private static boolean allowedTimeframe(JsonNode screener, String timeframe) {
        if (timeframe == null || timeframe.isBlank()) return false;
        JsonNode allowed = screener.path("allowed_timeframes");
        if (!allowed.isArray()) return false;
        for (JsonNode value : allowed) if (timeframe.equalsIgnoreCase(value.asText())) return true;
        return false;
    }
    private static String identifier(JsonNode node) {
        for (String name : List.of("screener_id", "id", "value", "code", "key"))
            if (node.hasNonNull(name) && node.path(name).isValueNode()) return node.path(name).asText().trim();
        return "";
    }
    private static List<String> symbols(JsonNode root) {
        var values = new java.util.LinkedHashSet<String>(); collectSymbols(root, values); return List.copyOf(values);
    }
    private static void collectSymbols(JsonNode node, Set<String> result) {
        if (node == null) return;
        if (node.isTextual()) {
            var matcher = FYERS_EQUITY.matcher(node.asText().toUpperCase(Locale.ROOT)); while (matcher.find()) result.add(matcher.group());
        } else if (node.isContainerNode()) node.forEach(child -> collectSymbols(child, result));
    }
    private static Map<String, JsonNode> quoteMap(JsonNode root) {
        var result = new LinkedHashMap<String, JsonNode>(); collectQuotes(root, result); return result;
    }
    private static void collectQuotes(JsonNode node, Map<String, JsonNode> result) {
        if (node == null) return;
        if (node.isObject()) {
            String symbol = node.path("n").asText(node.path("symbol").asText()).toUpperCase(Locale.ROOT);
            if (!symbol.isBlank() && (node.has("v") || node.has("lp") || node.has("ltp"))) result.put(symbol, node);
            node.forEach(child -> collectQuotes(child, result));
        } else if (node.isArray()) node.forEach(child -> collectQuotes(child, result));
    }
    private static BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name)) try { return new BigDecimal(node.path(name).asText().replace(",", "")); }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(names[0] + " is unavailable");
    }
    private static Instant epoch(JsonNode node, Instant fallback) {
        if (!node.canConvertToLong()) return fallback; long value = node.asLong(); if (value <= 0) return fallback;
        return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    }
    private static String normalize(String value) {
        String symbol = Objects.requireNonNullElse(value, "").toUpperCase(Locale.ROOT);
        if (symbol.startsWith("NSE:")) symbol = symbol.substring(4); if (symbol.endsWith("-EQ")) symbol = symbol.substring(0, symbol.length() - 3); return symbol;
    }
    private static String providerSymbol(String value) {
        String symbol = normalize(value);
        if (symbol.isBlank() || !symbol.matches("[A-Z0-9&_.-]+"))
            throw new IllegalArgumentException("Invalid NSE equity symbol: " + value);
        return "NSE:" + symbol + "-EQ";
    }
    private static List<String> universeTerms(String value) {
        String normalized = Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if (normalized.isBlank()) return List.of("nifty 500");
        if (normalized.contains("all nse") || normalized.contains("all equities")) return List.of("all stocks", "all equities");
        return List.of(normalized);
    }
    private static String message(Throwable failure) { return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
}
