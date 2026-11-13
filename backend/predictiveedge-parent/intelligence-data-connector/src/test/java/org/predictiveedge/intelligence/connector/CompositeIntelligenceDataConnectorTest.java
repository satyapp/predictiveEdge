package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.DecisionResourceType;

class CompositeIntelligenceDataConnectorTest {
    private static final Instant NOW = Instant.parse("2026-11-13T06:30:00Z");
    private static final EvidenceQuery QUERY = EvidenceQuery.forSymbol("INFY", "Infosys outlook", NOW);

    @Test
    void fetchesConfiguredSourcesAndReportsMissingSourcesWithoutSilentFallback() {
        EvidenceSourceConnector nse = connector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, "nse-quote");
        var service = new CompositeIntelligenceDataConnector(List.of(nse), Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(evidence).extracting(ProviderEvidence::source)
                .containsExactly(EvidenceSource.NSE, EvidenceSource.ZERODHA, EvidenceSource.TRENDLYNE);
        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.SUCCESS, EvidenceFetchStatus.UNAVAILABLE,
                        EvidenceFetchStatus.UNAVAILABLE);
        assertThat(evidence.getFirst().payload()).isEqualTo("nse-quote");
    }

    @Test
    void distinguishesUnsupportedFromUnavailable() {
        EvidenceSourceConnector nse = connector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, "unused");
        var service = new CompositeIntelligenceDataConnector(List.of(nse), Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.NEWS_AND_SENTIMENT, QUERY);

        assertThat(evidence.getFirst().status()).isEqualTo(EvidenceFetchStatus.UNSUPPORTED);
        assertThat(evidence.stream().skip(1)).allMatch(item -> item.status() == EvidenceFetchStatus.UNAVAILABLE);
    }

    @Test
    void assemblesEvidenceForEachResourceUsingItsDeclaredDomains() {
        var service = new CompositeIntelligenceDataConnector(List.of(), Clock.fixed(NOW, ZoneOffset.UTC));

        var bundle = service.fetchForResource(DecisionResourceType.CALIBRATION, QUERY);

        assertThat(bundle.resourceType()).isEqualTo(DecisionResourceType.CALIBRATION);
        assertThat(bundle.evidence()).extracting(ProviderEvidence::domain)
                .containsOnly(EvidenceDomain.HISTORICAL_AND_PREDICTIVE);
    }

    @Test
    void convertsConnectorExceptionsIntoFailedEvidence() {
        EvidenceSourceConnector failing = new EvidenceSourceConnector() {
            @Override public EvidenceSource source() { return EvidenceSource.NSE; }
            @Override public boolean supports(EvidenceDomain domain) { return true; }
            @Override public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
                throw new IllegalStateException("sensitive upstream detail");
            }
        };
        var service = new CompositeIntelligenceDataConnector(List.of(failing), Clock.fixed(NOW, ZoneOffset.UTC));

        ProviderEvidence result = service.fetch(EvidenceDomain.MARKET_DATA, QUERY).getFirst();

        assertThat(result.status()).isEqualTo(EvidenceFetchStatus.FAILED);
        assertThat(result.detail()).isEqualTo("Connector failure: IllegalStateException");
        assertThat(result.detail()).doesNotContain("sensitive");
    }

    private static EvidenceSourceConnector connector(
            EvidenceSource source, EvidenceDomain supported, Object payload) {
        return new EvidenceSourceConnector() {
            @Override public EvidenceSource source() { return source; }
            @Override public boolean supports(EvidenceDomain domain) { return domain == supported; }
            @Override public Object fetch(EvidenceDomain domain, EvidenceQuery query) { return payload; }
        };
    }
}
