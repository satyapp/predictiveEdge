package org.predictiveedge.intelligence.connector;

import java.util.Set;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

/**
 * FYERS evidence. FYERS is the first route for every domain it can supply, so it only serves
 * market data and historical candles. Other domains fall through to the remaining sources.
 */
public final class FyersEvidenceConnector implements EvidenceSourceConnector {
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(EvidenceDomain.MARKET_DATA,
            EvidenceDomain.HISTORICAL_AND_PREDICTIVE);
    /** May be null when the FYERS historical provider is not configured; fetches then report unavailable. */
    private final HistoricalMarketDataProvider history;

    public FyersEvidenceConnector(HistoricalMarketDataProvider history) {
        this.history = history;
    }

    @Override public EvidenceSource source() { return EvidenceSource.FYERS; }
    @Override public boolean supports(EvidenceDomain domain) { return SUPPORTED.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (history == null) throw new EvidenceUnavailableException("FYERS historical provider is not configured");
        if (query.brokerContext() == null || query.historicalDataRequest() == null) {
            throw new EvidenceUnavailableException(
                    "FYERS evidence requires brokerContext and historicalDataRequest");
        }
        return history.historicalCandles(query.brokerContext(), query.historicalDataRequest());
    }
}
