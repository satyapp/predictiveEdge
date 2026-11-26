package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.function.Function;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.EvidenceSourceConnector;
import org.predictiveedge.intelligence.connector.EvidenceUnavailableException;

/**
 * Serves FYERS market depth (top five bid and ask levels with totals and OHLC) for one stock per call, using the
 * connected FYERS session. Other FYERS domains stay with the historical connector.
 */
final class FyersMarketDepthEvidenceConnector implements EvidenceSourceConnector {
    private final Function<UUID, String> authorization;
    private final FyersHttpClient client;

    FyersMarketDepthEvidenceConnector(FyersConnectionService connections, FyersHttpClient client) {
        this(connections::authorization, client);
    }

    FyersMarketDepthEvidenceConnector(Function<UUID, String> authorization, FyersHttpClient client) {
        this.authorization = authorization;
        this.client = client;
    }

    @Override public EvidenceSource source() { return EvidenceSource.FYERS; }

    @Override public boolean supports(EvidenceDomain domain) { return domain == EvidenceDomain.MARKET_DEPTH; }

    /**
     * Returns the depth object for the requested symbol as FYERS sent it. The raw object is kept so the stored
     * evidence matches what the provider returned.
     */
    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (!supports(domain)) throw new EvidenceUnavailableException("FYERS does not expose " + domain);
        if (query.brokerContext() == null) {
            throw new EvidenceUnavailableException("FYERS market depth requires brokerContext");
        }
        String symbol = "NSE:" + query.symbol() + "-EQ";
        JsonNode response;
        try {
            response = client.depth(authorization.apply(query.brokerContext().userId()), symbol);
        } catch (IllegalStateException unavailable) {
            throw new EvidenceUnavailableException(unavailable.getMessage());
        }
        JsonNode depth = response.path("d").path(symbol);
        if (!depth.isObject()) {
            throw new EvidenceUnavailableException("FYERS returned no market depth for " + symbol);
        }
        return depth;
    }
}
