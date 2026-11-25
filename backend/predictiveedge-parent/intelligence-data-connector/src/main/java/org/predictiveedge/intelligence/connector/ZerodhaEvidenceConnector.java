package org.predictiveedge.intelligence.connector;

import java.util.Set;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

public final class ZerodhaEvidenceConnector implements EvidenceSourceConnector {
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(EvidenceDomain.MARKET_DATA,
            EvidenceDomain.MACRO_AND_ECONOMIC, EvidenceDomain.HISTORICAL_AND_PREDICTIVE);
    /** {@code history} may be null when the Zerodha historical provider is not configured; fetches then report unavailable. */
    private final HistoricalMarketDataProvider history;

    public ZerodhaEvidenceConnector(HistoricalMarketDataProvider history) {
        this.history = history;
    }

    @Override public EvidenceSource source() { return EvidenceSource.ZERODHA; }
    @Override public boolean supports(EvidenceDomain domain) { return SUPPORTED.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (history == null) throw new EvidenceUnavailableException("Zerodha historical provider is not configured");
        if (query.brokerContext() == null || query.historicalDataRequest() == null) {
            throw new EvidenceUnavailableException(
                    "Zerodha evidence requires brokerContext and historicalDataRequest");
        }
        return history.historicalCandles(query.brokerContext(), query.historicalDataRequest());
    }
}
