package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;

class PreOpenCsvAnalysisServiceTest {
    private static final String CSV = """
            SYMBOL,PREV. CLOSE,BEST BID PRICE,BEST ASK PRICE,FINAL PRICE,FINAL VOLUME,VALUE
            BHARTIARTL,"2,085.50","2,085.50","2,086.00","2,086.00","52,569","10,96,58,934.00"
            SBIN,"1,016.00","1,030.00","1,030.40","1,030.00","5,73,581","59,07,88,430.00"
            """;
    private static final Instant OBSERVED_AT = Instant.parse("2026-11-09T03:39:30Z");

    @Test
    void savesEveryValidRowMostTradedFirstAndDoesNoAnalysis() {
        var publications = new AtomicInteger();
        var publication = (org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort)
                (userId, accountId, observation, receivedAt) -> {
                    publications.incrementAndGet();
                    return new PreOpenAuctionSnapshot(UUID.randomUUID(), userId, accountId, observation,
                            receivedAt, "a".repeat(64));
                };
        var service = new PreOpenCsvAnalysisService(new NsePreOpenCsvParser(), publication,
                Clock.fixed(OBSERVED_AT.plusSeconds(60), ZoneOffset.UTC));

        var result = service.ingest(new PreOpenCsvAnalysisService.Command(UUID.randomUUID(), "ZD123", CSV,
                LocalDate.of(2026, 11, 9), OBSERVED_AT));

        assertThat(result.parsedRows()).isEqualTo(2);
        assertThat(result.rejectedRows()).isEmpty();
        assertThat(publications).hasValue(2);
        assertThat(result.validRows()).extracting(row -> row.symbol()).containsExactly("SBIN", "BHARTIARTL");
    }

    @Test
    void refusesACsvThatHasNoValidRowsToSave() {
        var publication = (org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort)
                (userId, accountId, observation, receivedAt) -> {
                    throw new AssertionError("nothing should be published");
                };
        var service = new PreOpenCsvAnalysisService(new NsePreOpenCsvParser(), publication,
                Clock.fixed(OBSERVED_AT.plusSeconds(60), ZoneOffset.UTC));
        String headerOnly = "SYMBOL,PREV. CLOSE,BEST BID PRICE,BEST ASK PRICE,FINAL PRICE,FINAL VOLUME,VALUE\n";

        assertThatThrownBy(() -> service.ingest(new PreOpenCsvAnalysisService.Command(UUID.randomUUID(), "ZD123",
                headerOnly, LocalDate.of(2026, 11, 9), OBSERVED_AT)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
