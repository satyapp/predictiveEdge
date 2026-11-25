package org.predictiveedge.fyers;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.EvidenceSourceConnector;
import org.predictiveedge.intelligence.connector.EvidenceUnavailableException;

/** FYERS-first evidence for quotes, five-level depth and current pre-open/market state. */
final class FyersEvidenceConnector implements EvidenceSourceConnector {
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(
            EvidenceDomain.MARKET_DATA, EvidenceDomain.MARKET_DEPTH,
            EvidenceDomain.PRE_OPEN_ANALYSIS, EvidenceDomain.HISTORICAL_AND_PREDICTIVE);
    private final FyersConnectionService connections;
    private final FyersHttpClient client;

    FyersEvidenceConnector(FyersConnectionService connections, FyersHttpClient client) {
        this.connections = java.util.Objects.requireNonNull(connections);
        this.client = java.util.Objects.requireNonNull(client);
    }

    @Override public EvidenceSource source() { return EvidenceSource.FYERS; }
    @Override public boolean supports(EvidenceDomain domain) { return SUPPORTED.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (query.brokerContext() == null)
            throw new EvidenceUnavailableException("FYERS evidence requires an authenticated broker context");
        String authorization = connections.authorization(query.brokerContext().userId());
        String symbol = providerSymbol(query.symbol());
        return switch (domain) {
            case MARKET_DATA -> client.quotes(authorization, symbol);
            case MARKET_DEPTH -> client.depth(authorization, symbol);
            case PRE_OPEN_ANALYSIS -> Map.of(
                    "symbol_quote", client.quotes(authorization, symbol),
                    "market_status", client.marketStatus(authorization));
            case HISTORICAL_AND_PREDICTIVE -> {
                if (query.historicalDataRequest() == null)
                    throw new EvidenceUnavailableException(
                            "FYERS historical evidence is already represented by the deterministic candle analysis");
                yield client.history(authorization, Map.of(
                        "symbol", symbol,
                        "resolution", "5",
                        "date_format", "0",
                        "range_from", Long.toString(query.historicalDataRequest().from().getEpochSecond()),
                        "range_to", Long.toString(query.historicalDataRequest().to().getEpochSecond()),
                        "cont_flag", query.historicalDataRequest().continuous() ? "1" : "0",
                        "oi_flag", query.historicalDataRequest().includeOpenInterest() ? "1" : "0"));
            }
            default -> throw new IllegalArgumentException("Unsupported FYERS evidence domain: " + domain);
        };
    }

    private static String providerSymbol(String value) {
        String symbol = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (symbol.startsWith("NSE:")) symbol = symbol.substring(4);
        if (symbol.endsWith("-EQ")) symbol = symbol.substring(0, symbol.length() - 3);
        if (symbol.isBlank() || !symbol.matches("[A-Z0-9&_.-]+"))
            throw new IllegalArgumentException("Invalid NSE equity symbol: " + value);
        return "NSE:" + symbol + "-EQ";
    }
}
