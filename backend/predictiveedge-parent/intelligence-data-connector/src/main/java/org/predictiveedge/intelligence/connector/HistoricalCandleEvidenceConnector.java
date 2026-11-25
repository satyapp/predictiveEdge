package org.predictiveedge.intelligence.connector;

import java.util.Objects;
import java.util.Set;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

/**
 * Serves historical candles from one broker's existing historical provider, in a single call per fetch.
 * {@code history} may be null when that broker is not configured; fetches then report unavailable.
 */
public final class HistoricalCandleEvidenceConnector implements EvidenceSourceConnector {
    private final EvidenceSource source;
    private final Set<EvidenceDomain> supported;
    private final HistoricalMarketDataProvider history;

    public HistoricalCandleEvidenceConnector(EvidenceSource source, Set<EvidenceDomain> supported,
            HistoricalMarketDataProvider history) {
        this.source = Objects.requireNonNull(source, "Evidence source is required");
        this.supported = Set.copyOf(supported);
        this.history = history;
    }

    @Override public EvidenceSource source() { return source; }
    @Override public boolean supports(EvidenceDomain domain) { return supported.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (history == null) throw new EvidenceUnavailableException(source + " historical provider is not configured");
        if (query.brokerContext() == null || query.historicalDataRequest() == null) {
            throw new EvidenceUnavailableException(source + " evidence requires brokerContext and historicalDataRequest");
        }
        return history.historicalCandles(query.brokerContext(), query.historicalDataRequest());
    }
}
