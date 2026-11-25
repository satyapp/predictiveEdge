package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HistoricalCandleEvidenceConnectorTest {
    private static final EvidenceQuery QUERY = EvidenceQuery.forSymbol("INFY", "Infosys", Instant.parse(
            "2026-11-25T06:00:00Z"));

    @Test
    void servesOnlyTheDomainsItIsGiven() {
        var fyers = new HistoricalCandleEvidenceConnector(EvidenceSource.FYERS,
                Set.of(EvidenceDomain.MARKET_DATA, EvidenceDomain.HISTORICAL_AND_PREDICTIVE), null);

        assertThat(fyers.source()).isEqualTo(EvidenceSource.FYERS);
        assertThat(fyers.supports(EvidenceDomain.MARKET_DATA)).isTrue();
        assertThat(fyers.supports(EvidenceDomain.HISTORICAL_AND_PREDICTIVE)).isTrue();
        assertThat(fyers.supports(EvidenceDomain.MACRO_AND_ECONOMIC)).isFalse();
        assertThat(fyers.supports(EvidenceDomain.MARKET_DEPTH)).isFalse();
    }

    @Test
    void reportsUnavailableWhenProviderIsNotConfigured() {
        var connector = new HistoricalCandleEvidenceConnector(EvidenceSource.FYERS,
                Set.of(EvidenceDomain.MARKET_DATA), null);

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DATA, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void reportsUnavailableWithoutBrokerContextAndNeverCallsTheProvider() {
        var connector = new HistoricalCandleEvidenceConnector(EvidenceSource.FYERS,
                Set.of(EvidenceDomain.MARKET_DATA), (context, request) -> {
                    throw new AssertionError("provider must not be called without a broker context");
                });

        assertThatThrownBy(() -> connector.fetch(EvidenceDomain.MARKET_DATA, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class);
    }
}
