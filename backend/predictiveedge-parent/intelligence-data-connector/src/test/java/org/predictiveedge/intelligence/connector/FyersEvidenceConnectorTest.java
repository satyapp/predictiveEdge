package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class FyersEvidenceConnectorTest {
    private static final EvidenceQuery QUERY = EvidenceQuery.forSymbol("INFY", "Infosys", Instant.parse(
            "2026-11-25T06:00:00Z"));

    @Test
    void servesOnlyMarketDataAndHistoricalDomains() {
        var connector = new FyersEvidenceConnector(null);

        assertThat(connector.source()).isEqualTo(EvidenceSource.FYERS);
        assertThat(connector.supports(EvidenceDomain.MARKET_DATA)).isTrue();
        assertThat(connector.supports(EvidenceDomain.HISTORICAL_AND_PREDICTIVE)).isTrue();
        assertThat(connector.supports(EvidenceDomain.MACRO_AND_ECONOMIC)).isFalse();
        assertThat(connector.supports(EvidenceDomain.NEWS_AND_SENTIMENT)).isFalse();
    }

    @Test
    void reportsUnavailableWhenProviderIsNotConfigured() {
        var connector = new FyersEvidenceConnector(null);

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DATA, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void reportsUnavailableWithoutBrokerContext() {
        var connector = new FyersEvidenceConnector((context, request) -> {
            throw new AssertionError("provider must not be called without a broker context");
        });

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DATA, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class);
    }
}
