package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NsePreOpenCsvParserTest {
    private static final String HEADER = "SYMBOL,PREV. CLOSE,BEST BID PRICE,BEST ASK PRICE,FINAL PRICE,FINAL VOLUME,VALUE\n";

    @Test
    void parsesQuotedIndianNumbersAndRejectsRowsWithoutFinalPrice() {
        String csv = HEADER
                + "SBIN,\"1,016.00\",\"1,030.00\",\"1,030.40\",\"1,030.00\",\"5,73,581\",\"59,07,88,430.00\"\n"
                + "MISSING,100,101,102,-,-,-\n";

        var result = new NsePreOpenCsvParser().parse(csv, Instant.parse("2026-11-09T03:39:30Z"));

        assertThat(result.snapshots()).hasSize(1);
        assertThat(result.snapshots().getFirst().symbol()).isEqualTo("SBIN");
        assertThat(result.snapshots().getFirst().auctionVolume()).isEqualTo(573_581);
        assertThat(result.snapshots().getFirst().auctionTurnover()).isEqualByComparingTo("590788430.00");
        assertThat(result.rejectedRows()).singleElement().satisfies(rejection -> {
            assertThat(rejection.symbol()).isEqualTo("MISSING");
            assertThat(rejection.reason()).contains("FINAL PRICE");
        });
    }
}
