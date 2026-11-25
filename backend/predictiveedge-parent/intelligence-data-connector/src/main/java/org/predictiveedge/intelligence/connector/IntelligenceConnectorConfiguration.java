package org.predictiveedge.intelligence.connector;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.nse.mcp.NseMcpGateway;
import org.predictiveedge.trendlyne.mcp.TrendlyneMcpGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntelligenceConnectorConfiguration {
    @Bean @ConditionalOnBean(NseMcpGateway.class)
    NseEvidenceConnector nseEvidenceConnector(NseMcpGateway gateway) { return new NseEvidenceConnector(gateway); }

    @Bean @ConditionalOnBean(TrendlyneMcpGateway.class)
    TrendlyneEvidenceConnector trendlyneEvidenceConnector(TrendlyneMcpGateway gateway) {
        return new TrendlyneEvidenceConnector(gateway);
    }

    /**
     * Named lookups, not type injection: FYERS is the @Primary historical provider, so an unqualified
     * injection would send Zerodha routes to FYERS and make a second, duplicate call.
     */
    @Bean @ConditionalOnBean(HistoricalMarketDataProvider.class)
    HistoricalCandleEvidenceConnector zerodhaEvidenceConnector(
            @Qualifier("zerodhaHistoricalDataProvider") ObjectProvider<HistoricalMarketDataProvider> history) {
        return new HistoricalCandleEvidenceConnector(EvidenceSource.ZERODHA,
                Set.of(EvidenceDomain.MARKET_DATA, EvidenceDomain.MACRO_AND_ECONOMIC,
                        EvidenceDomain.HISTORICAL_AND_PREDICTIVE),
                history.getIfAvailable());
    }

    @Bean
    HistoricalCandleEvidenceConnector fyersEvidenceConnector(
            @Qualifier("fyersHistoricalDataProvider") ObjectProvider<HistoricalMarketDataProvider> history) {
        return new HistoricalCandleEvidenceConnector(EvidenceSource.FYERS,
                Set.of(EvidenceDomain.MARKET_DATA, EvidenceDomain.HISTORICAL_AND_PREDICTIVE),
                history.getIfAvailable());
    }

    @Bean
    IntelligenceDataConnector intelligenceDataConnector(List<EvidenceSourceConnector> connectors) {
        return new CompositeIntelligenceDataConnector(connectors, Clock.systemUTC());
    }
}
