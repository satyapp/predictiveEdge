package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Routes a declared screen type to its exact schema adapter. */
public final class MarketScreenCsvParserRegistry {
    private final Map<MarketScreenType, MarketScreenCsvParser> parsers;

    public MarketScreenCsvParserRegistry(List<MarketScreenCsvParser> adapters) {
        Objects.requireNonNull(adapters, "CSV parser adapters are required");
        var registered = new EnumMap<MarketScreenType, MarketScreenCsvParser>(MarketScreenType.class);
        for (MarketScreenCsvParser adapter : adapters) {
            for (MarketScreenType type : adapter.supportedTypes()) {
                if (registered.put(type, adapter) != null)
                    throw new IllegalArgumentException("Duplicate CSV parser for " + type);
            }
        }
        parsers = Map.copyOf(registered);
    }

    public Set<MarketScreenType> supportedTypes() { return parsers.keySet(); }

    public MarketScreenCsvParser.ParseResult parse(MarketScreenType type, String csv, Instant observedAt) {
        MarketScreenCsvParser parser = parsers.get(type);
        if (parser == null) throw new IllegalArgumentException("CSV format is not implemented yet for " + type);
        return parser.parse(type, csv, observedAt);
    }
}
