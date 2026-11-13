package org.predictiveedge.intelligence.connector;

import java.util.Set;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;

public final class ZerodhaEvidenceConnector implements EvidenceSourceConnector {
    private static final Set<EvidenceDomain> SUPPORTED = Set.of(EvidenceDomain.MARKET_DATA,
            EvidenceDomain.MACRO_AND_ECONOMIC, EvidenceDomain.HISTORICAL_AND_PREDICTIVE);
    private final HistoricalMarketDataProvider history;

    public ZerodhaEvidenceConnector(HistoricalMarketDataProvider history) {
        this.history = java.util.Objects.requireNonNull(history, "Zerodha historical provider is required");
    }

    @Override public EvidenceSource source() { return EvidenceSource.ZERODHA; }
    @Override public boolean supports(EvidenceDomain domain) { return SUPPORTED.contains(domain); }

    @Override
    public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (query.brokerContext() == null || query.historicalDataRequest() == null) {
            throw new EvidenceUnavailableException(
                    "Zerodha evidence requires brokerContext and historicalDataRequest");
        }
        return history.historicalCandles(query.brokerContext(), query.historicalDataRequest());
    }
}
