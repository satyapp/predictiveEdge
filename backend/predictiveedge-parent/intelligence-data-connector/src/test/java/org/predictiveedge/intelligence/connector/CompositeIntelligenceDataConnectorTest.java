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
                .containsExactly(EvidenceSource.FYERS, EvidenceSource.NSE, EvidenceSource.ZERODHA,
                        EvidenceSource.TRENDLYNE);
        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.UNAVAILABLE, EvidenceFetchStatus.SUCCESS,
                        EvidenceFetchStatus.SKIPPED, EvidenceFetchStatus.SKIPPED);
        assertThat(evidence.get(1).payload()).isEqualTo("nse-quote");
    }

    @Test
    void stopsAtFirstUsableSourceAndDoesNotCallLaterSources() {
        var zerodha = new CountingConnector(EvidenceSource.ZERODHA, EvidenceDomain.MARKET_DATA, "zerodha-quote");
        var trendlyne = new CountingConnector(EvidenceSource.TRENDLYNE, EvidenceDomain.MARKET_DATA, "trendlyne");
        var nse = new CountingConnector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, "nse-quote");
        var service = new CompositeIntelligenceDataConnector(List.of(nse, zerodha, trendlyne),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(nse.calls).isEqualTo(1);
        assertThat(zerodha.calls).isZero();
        assertThat(trendlyne.calls).isZero();
        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.UNAVAILABLE, EvidenceFetchStatus.SUCCESS,
                        EvidenceFetchStatus.SKIPPED, EvidenceFetchStatus.SKIPPED);
        assertThat(evidence.stream().filter(item -> item.status() == EvidenceFetchStatus.SUCCESS)
                .map(ProviderEvidence::source)).containsExactly(EvidenceSource.NSE);
        assertThat(evidence.get(2).detail()).contains("earlier source");
    }

    @Test
    void fyersAnswersMarketDataFirstAndNoLaterSourceIsCalled() {
        var fyers = new CountingConnector(EvidenceSource.FYERS, EvidenceDomain.MARKET_DATA, "fyers-quote");
        var nse = new CountingConnector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, "nse-quote");
        var zerodha = new CountingConnector(EvidenceSource.ZERODHA, EvidenceDomain.MARKET_DATA, "zerodha-quote");
        var service = new CompositeIntelligenceDataConnector(List.of(zerodha, nse, fyers),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(evidence).extracting(ProviderEvidence::source)
                .containsExactly(EvidenceSource.FYERS, EvidenceSource.NSE, EvidenceSource.ZERODHA,
                        EvidenceSource.TRENDLYNE);
        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.SUCCESS, EvidenceFetchStatus.SKIPPED,
                        EvidenceFetchStatus.SKIPPED, EvidenceFetchStatus.SKIPPED);
        assertThat(fyers.calls).isEqualTo(1);
        assertThat(nse.calls).isZero();
        assertThat(zerodha.calls).isZero();
    }

    @Test
    void fyersUnavailableFallsBackToNse() {
        var fyers = new CountingConnector(EvidenceSource.FYERS, EvidenceDomain.MARKET_DATA, null);
        var nse = new CountingConnector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, "nse-quote");
        var service = new CompositeIntelligenceDataConnector(List.of(fyers, nse),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(evidence.get(0).status()).isEqualTo(EvidenceFetchStatus.UNAVAILABLE);
        assertThat(evidence.get(1).status()).isEqualTo(EvidenceFetchStatus.SUCCESS);
        assertThat(fyers.calls).isEqualTo(1);
        assertThat(nse.calls).isEqualTo(1);
    }

    @Test
    void fallsThroughToNextSourceWhenEarlierSourceReturnsNothingUsable() {
        var nse = new CountingConnector(EvidenceSource.NSE, EvidenceDomain.MARKET_DATA, List.of());
        var zerodha = new CountingConnector(EvidenceSource.ZERODHA, EvidenceDomain.MARKET_DATA, "zerodha-quote");
        var trendlyne = new CountingConnector(EvidenceSource.TRENDLYNE, EvidenceDomain.MARKET_DATA, "trendlyne");
        var service = new CompositeIntelligenceDataConnector(List.of(nse, zerodha, trendlyne),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(nse.calls).isEqualTo(1);
        assertThat(zerodha.calls).isEqualTo(1);
        assertThat(trendlyne.calls).isZero();
        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.UNAVAILABLE, EvidenceFetchStatus.UNAVAILABLE,
                        EvidenceFetchStatus.SUCCESS, EvidenceFetchStatus.SKIPPED);
        assertThat(evidence.get(2).payload()).isEqualTo("zerodha-quote");
    }

    @Test
    void fallsThroughWhenEarlierSourceFailsOrIsUnsupported() {
        var failing = new EvidenceSourceConnector() {
            @Override public EvidenceSource source() { return EvidenceSource.NSE; }
            @Override public boolean supports(EvidenceDomain domain) { return true; }
            @Override public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
                throw new IllegalStateException("upstream down");
            }
        };
        var trendlyne = new CountingConnector(EvidenceSource.TRENDLYNE, EvidenceDomain.MARKET_DATA, "trendlyne");
        var service = new CompositeIntelligenceDataConnector(List.of(failing, trendlyne),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var evidence = service.fetch(EvidenceDomain.MARKET_DATA, QUERY);

        assertThat(evidence).extracting(ProviderEvidence::status)
                .containsExactly(EvidenceFetchStatus.UNAVAILABLE, EvidenceFetchStatus.FAILED,
                        EvidenceFetchStatus.UNAVAILABLE, EvidenceFetchStatus.SUCCESS);
        assertThat(trendlyne.calls).isEqualTo(1);
    }

    @Test
    void callsEachSourceAtMostOncePerFetch() {
        var nse = new CountingConnector(EvidenceSource.NSE, EvidenceDomain.CORPORATE_ACTIONS, null);
        var trendlyne = new CountingConnector(EvidenceSource.TRENDLYNE, EvidenceDomain.CORPORATE_ACTIONS, "tl");
        var service = new CompositeIntelligenceDataConnector(List.of(nse, trendlyne),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.fetch(EvidenceDomain.CORPORATE_ACTIONS, QUERY);

        assertThat(nse.calls).isEqualTo(1);
        assertThat(trendlyne.calls).isEqualTo(1);
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

        ProviderEvidence result = service.fetch(EvidenceDomain.CORPORATE_ACTIONS, QUERY).getFirst();

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

    /** Supports one domain, returns a fixed payload, and counts how many times it was called. */
    private static final class CountingConnector implements EvidenceSourceConnector {
        private final EvidenceSource source;
        private final EvidenceDomain supported;
        private final Object payload;
        private int calls;

        CountingConnector(EvidenceSource source, EvidenceDomain supported, Object payload) {
            this.source = source;
            this.supported = supported;
            this.payload = payload;
        }

        @Override public EvidenceSource source() { return source; }
        @Override public boolean supports(EvidenceDomain domain) { return domain == supported; }

        @Override
        public Object fetch(EvidenceDomain domain, EvidenceQuery query) {
            calls++;
            return payload;
        }
    }
}
