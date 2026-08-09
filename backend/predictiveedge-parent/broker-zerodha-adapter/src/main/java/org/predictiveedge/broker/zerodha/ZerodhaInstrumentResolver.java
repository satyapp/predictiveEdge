package org.predictiveedge.broker.zerodha;

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

/** Daily-cached resolver for Kite's instrument CSV master. */
public final class ZerodhaInstrumentResolver implements LiveMarketDataInstrumentResolver {
    private static final URI INSTRUMENTS_URI = URI.create("https://api.kite.trade/instruments");
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private final ZerodhaSessionProvider sessions;
    private final ZerodhaTransport transport;
    private final Clock clock;
    private LocalDate catalogDate;
    private Map<Instrument, LiveMarketDataInstrument> catalog = Map.of();

    public ZerodhaInstrumentResolver(ZerodhaSessionProvider sessions, ZerodhaTransport transport, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "Session provider is required");
        this.transport = Objects.requireNonNull(transport, "Transport is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public synchronized List<LiveMarketDataInstrument> resolve(
            BrokerContext context, List<Instrument> instruments) {
        Objects.requireNonNull(context, "Broker context is required");
        Objects.requireNonNull(instruments, "Instruments are required");
        if (instruments.isEmpty()) throw new IllegalArgumentException("At least one instrument is required");
        if (instruments.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Instrument cannot be null");
        if (new HashSet<>(instruments).size() != instruments.size())
            throw new IllegalArgumentException("Instruments must be unique");

        var session = sessions.sessionFor(context);
        var today = LocalDate.now(clock.withZone(MARKET_ZONE));
        if (!today.equals(catalogDate)) {
            catalog = download(session);
            catalogDate = today;
        }

        var resolved = new ArrayList<LiveMarketDataInstrument>(instruments.size());
        for (Instrument instrument : instruments) {
            var value = catalog.get(instrument);
            if (value == null)
                throw new IllegalArgumentException("Zerodha instrument is unavailable: "
                        + instrument.exchange() + ":" + instrument.symbol());
            resolved.add(value);
        }
        return List.copyOf(resolved);
    }

    private Map<Instrument, LiveMarketDataInstrument> download(ZerodhaSession session) {
        String body = transport.get(INSTRUMENTS_URI, Map.of(
                "X-Kite-Version", "3",
                "Authorization", "token " + session.apiKey() + ":" + session.accessToken()));
        return parse(body);
    }

    static Map<Instrument, LiveMarketDataInstrument> parse(String csv) {
        try {
            var rows = rows(csv);
            if (rows.size() < 2) throw malformed("instrument master is empty");
            var columns = columnIndexes(rows.getFirst());
            int tokenIndex = required(columns, "instrument_token");
            int symbolIndex = required(columns, "tradingsymbol");
            int typeIndex = required(columns, "instrument_type");
            int segmentIndex = required(columns, "segment");
            int exchangeIndex = required(columns, "exchange");
            int expectedColumns = rows.getFirst().size();
            var parsed = new LinkedHashMap<Instrument, LiveMarketDataInstrument>();
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                var row = rows.get(rowIndex);
                if (row.size() != expectedColumns) throw malformed("instrument row has an invalid column count");
                var kind = supportedKind(row.get(segmentIndex), row.get(typeIndex));
                if (kind == null) continue;
                String token = row.get(tokenIndex).trim();
                if (!token.matches("[0-9]+")) throw malformed("instrument token is not numeric");
                var instrument = new Instrument(row.get(exchangeIndex), row.get(symbolIndex));
                var previous = parsed.putIfAbsent(instrument,
                        new LiveMarketDataInstrument(instrument, token, kind));
                if (previous != null && !previous.providerInstrumentId().equals(token))
                    throw malformed("instrument identity maps to multiple tokens");
            }
            if (parsed.isEmpty()) throw malformed("instrument master contains no supported instruments");
            return Map.copyOf(parsed);
        } catch (BrokerFailure failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw malformed("instrument master could not be parsed");
        }
    }

    private static MarketDataInstrumentKind supportedKind(String segmentValue, String typeValue) {
        String segment = segmentValue.trim().toUpperCase(Locale.ROOT);
        String type = typeValue.trim().toUpperCase(Locale.ROOT);
        if ("INDICES".equals(segment)) return MarketDataInstrumentKind.INDEX;
        if (("NSE".equals(segment) || "BSE".equals(segment)) && "EQ".equals(type))
            return MarketDataInstrumentKind.EQUITY;
        return null;
    }

    private static Map<String, Integer> columnIndexes(List<String> header) {
        var indexes = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < header.size(); index++) {
            String name = header.get(index).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
            indexes.put(name, index);
        }
        return indexes;
    }

    private static int required(Map<String, Integer> columns, String name) {
        var index = columns.get(name);
        if (index == null) throw malformed("instrument master is missing " + name);
        return index;
    }

    private static List<List<String>> rows(String csv) {
        if (csv == null || csv.isBlank()) throw malformed("instrument master is empty");
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char value = csv.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else quoted = false;
                } else field.append(value);
            } else if (value == '"') {
                if (!field.isEmpty()) throw malformed("instrument CSV quote is misplaced");
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (value == '\r' || value == '\n') {
                if (value == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') index++;
                row.add(field.toString());
                field.setLength(0);
                if (row.stream().anyMatch(cell -> !cell.isBlank())) rows.add(List.copyOf(row));
                row.clear();
            } else field.append(value);
        }
        if (quoted) throw malformed("instrument CSV contains an unterminated quote");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            if (row.stream().anyMatch(cell -> !cell.isBlank())) rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static BrokerFailure malformed(String reason) {
        return new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE, "Zerodha " + reason);
    }
}
