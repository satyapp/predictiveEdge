package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.chart.domain.IntradaySetupAction;
import org.predictiveedge.chart.domain.PreOpenCandidateRanker;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;

class PreOpenCsvAnalysisServiceTest {
    private static final String CSV = """
            SYMBOL,PREV. CLOSE,BEST BID PRICE,BEST ASK PRICE,FINAL PRICE,FINAL VOLUME,VALUE
            SBIN,"1,016.00","1,030.00","1,030.40","1,030.00","5,73,581","59,07,88,430.00"
            BHARTIARTL,"2,085.50","2,085.50","2,086.00","2,086.00","52,569","10,96,58,934.00"
            """;

    @Test
    void persistsAndFetchesHistoryOnlyForPreFilteredResolvedEquities() {
        var publications = new AtomicInteger();
        var historyCalls = new AtomicInteger();
        var resolver = (org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver) (context, requested) ->
                List.of(new LiveMarketDataInstrument(requested.getFirst(), "779521",
                        MarketDataInstrumentKind.EQUITY));
        var history = (org.predictiveedge.broker.spi.HistoricalMarketDataProvider) (context, request) -> {
            historyCalls.incrementAndGet();
            return List.of(candle(request, request.from()), candle(request, request.to()));
        };
        var publication = (org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort)
                (userId, accountId, observation, receivedAt) -> {
                    publications.incrementAndGet();
                    return new PreOpenAuctionSnapshot(UUID.randomUUID(), userId, accountId, observation,
                            receivedAt, "a".repeat(64));
                };
        Instant observedAt = Instant.parse("2026-11-09T03:39:30Z");
        var service = new PreOpenCsvAnalysisService(new NsePreOpenCsvParser(), resolver, history, publication,
                new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults()),
                new PreOpenCandidateRanker(),
                Clock.fixed(observedAt.plusSeconds(60), ZoneOffset.UTC));

        var result = service.analyze(new PreOpenCsvAnalysisService.Command(UUID.randomUUID(), "ZD123", CSV,
                LocalDate.of(2026, 11, 9), observedAt, observedAt.plusSeconds(30), Duration.ofDays(365), 10));

        assertThat(publications).hasValue(1);
        assertThat(historyCalls).hasValue(5);
        assertThat(result.parsedSnapshots()).isEqualTo(2);
        assertThat(result.shortlistedForHistory()).isEqualTo(1);
        assertThat(result.ranking().calibratedProbability()).isFalse();
        assertThat(result.ranking().probableGainers()).isEmpty();
        assertThat(result.ranking().probableLosers()).isEmpty();
        assertThat(result.analyses()).singleElement().satisfies(value -> {
            assertThat(value.assessment().symbol()).isEqualTo("SBIN");
            assertThat(value.assessment().action()).isEqualTo(IntradaySetupAction.INSUFFICIENT_EVIDENCE);
            assertThat(value.historicalBars()).isEqualTo(5);
        });
    }

    private static Candle candle(org.predictiveedge.broker.domain.HistoricalDataRequest request, Instant timestamp) {
        return new Candle(request.instrument(), timestamp, new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100.50"), 1000, null);
    }
}
