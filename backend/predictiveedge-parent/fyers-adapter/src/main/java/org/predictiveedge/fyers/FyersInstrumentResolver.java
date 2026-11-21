package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;

/** Daily-cached resolver backed by FYERS's authoritative capital-market symbol masters. */
public final class FyersInstrumentResolver implements LiveMarketDataInstrumentResolver {
    private static final String MASTER = "https://public.fyers.in/sym_details/%s_CM_sym_master.json";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private final FyersHttpClient client;
    private final Clock clock;
    private LocalDate catalogDate;
    private Map<Instrument, LiveMarketDataInstrument> catalog = Map.of();

    FyersInstrumentResolver(FyersHttpClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public synchronized List<LiveMarketDataInstrument> resolve(BrokerContext context, List<Instrument> instruments) {
        Objects.requireNonNull(context, "Broker context is required");
        Objects.requireNonNull(instruments, "Instruments are required");
        if (instruments.isEmpty()) throw new IllegalArgumentException("At least one instrument is required");
        if (instruments.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Instrument cannot be null");
        if (new HashSet<>(instruments).size() != instruments.size())
            throw new IllegalArgumentException("Instruments must be unique");

        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        if (!today.equals(catalogDate)) {
            catalog = download(instruments.stream().map(Instrument::exchange).collect(java.util.stream.Collectors.toSet()));
            catalogDate = today;
        }
        var resolved = new ArrayList<LiveMarketDataInstrument>(instruments.size());
        for (Instrument instrument : instruments) {
            var value = catalog.get(instrument);
            if (value == null) throw new IllegalArgumentException("FYERS instrument is unavailable: "
                    + instrument.exchange() + ":" + instrument.symbol());
            resolved.add(value);
        }
        return List.copyOf(resolved);
    }

    private Map<Instrument, LiveMarketDataInstrument> download(java.util.Set<String> exchanges) {
        var values = new LinkedHashMap<Instrument, LiveMarketDataInstrument>();
        for (String exchange : exchanges) {
            if (!List.of("NSE", "BSE").contains(exchange))
                throw new IllegalArgumentException("FYERS capital-market resolver does not support " + exchange);
            JsonNode root;
            try {
                root = client.publicJson(URI.create(MASTER.formatted(exchange)));
            } catch (IllegalStateException failure) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "FYERS symbol master is unavailable for " + exchange);
            }
            parse(root, values);
        }
        if (values.isEmpty()) throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                "FYERS symbol masters contain no supported instruments");
        return Map.copyOf(values);
    }

    private static void parse(JsonNode root, Map<Instrument, LiveMarketDataInstrument> target) {
        if (root == null || !root.isObject())
            throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE, "FYERS symbol master is malformed");
        root.fields().forEachRemaining(entry -> {
            JsonNode row = entry.getValue();
            String ticker = row.path("symTicker").asText(entry.getKey()).trim().toUpperCase(Locale.ROOT);
            Parsed parsed = parsed(ticker);
            if (parsed != null) {
                var candidate = new LiveMarketDataInstrument(parsed.instrument(), ticker, parsed.kind());
                target.merge(parsed.instrument(), candidate, FyersInstrumentResolver::preferred);
            }
        });
    }

    private static Parsed parsed(String ticker) {
        int colon = ticker.indexOf(':');
        if (colon < 1 || colon == ticker.length() - 1) return null;
        String exchange = ticker.substring(0, colon);
        String symbol = ticker.substring(colon + 1);
        MarketDataInstrumentKind kind;
        if (symbol.endsWith("-INDEX")) {
            symbol = symbol.substring(0, symbol.length() - 6);
            kind = MarketDataInstrumentKind.INDEX;
        } else {
            int series = symbol.lastIndexOf('-');
            if (series < 1 || series == symbol.length() - 1) return null;
            symbol = symbol.substring(0, series);
            kind = MarketDataInstrumentKind.EQUITY;
        }
        return new Parsed(new Instrument(exchange, symbol), kind);
    }

    private static LiveMarketDataInstrument preferred(
            LiveMarketDataInstrument existing, LiveMarketDataInstrument candidate) {
        return priority(candidate.providerInstrumentId()) > priority(existing.providerInstrumentId())
                ? candidate : existing;
    }

    private static int priority(String ticker) {
        if (ticker.endsWith("-EQ") || ticker.endsWith("-A")) return 2;
        if (ticker.endsWith("-INDEX")) return 2;
        return 1;
    }

    private record Parsed(Instrument instrument, MarketDataInstrumentKind kind) { }
}
