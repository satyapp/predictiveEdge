package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NsePreOpenCsvParserTest {
    private static final String HEADER = "SYMBOL,PREV. CLOSE,BEST BID PRICE,BEST ASK PRICE,FINAL PRICE,FINAL VOLUME,VALUE\n";

    @Test
    void mapsEveryNsePreOpenColumnIntoTheSnapshotDetail() {
        String fullHeader = "SYMBOL,PREV. CLOSE,BEST BID QTY,BEST BID PRICE,BEST ASK PRICE,BEST ASK QTY,"
                + "INDICATIVE EQUILIBRIUM QUANTITY,INDICATIVE EQUILIBRIUM PRICE,CHANGE,%CHANGE,FINAL PRICE,"
                + "FINAL VOLUME,VALUE,FFM CAP,INDICATIVE IMBALANCE QUANTITY AT EQUILIBRIUM PRICE,"
                + "INDICATIVE IMBALANCE QUANTITY AT MARKET ORDER,52 WEEK HIGH,52 WEEK LOW\n";
        String csv = fullHeader
                + "SBIN,\"1,016.00\",\"12,500\",\"1,030.00\",\"1,030.40\",\"3,000\",\"4,50,000\",\"1,029.90\","
                + "\"14.00\",\"1.38\",\"1,030.00\",\"5,73,581\",\"59,07,88,430.00\",\"2,50,000.55\",\"1,200\","
                + "\"-\",\"1,100.00\",\"780.25\"\n";

        var snapshot = new NsePreOpenCsvParser().parse(csv, Instant.parse("2026-11-09T03:39:30Z"))
                .snapshots().getFirst();

        var detail = snapshot.detail();
        assertThat(detail.previousClose()).isEqualByComparingTo("1016.00");
        assertThat(detail.bestBidPrice()).isEqualByComparingTo("1030.00");
        assertThat(detail.bestBidQuantity()).isEqualTo(12_500L);
        assertThat(detail.bestAskPrice()).isEqualByComparingTo("1030.40");
        assertThat(detail.bestAskQuantity()).isEqualTo(3_000L);
        assertThat(detail.indicativeEquilibriumPrice()).isEqualByComparingTo("1029.90");
        assertThat(detail.indicativeEquilibriumQuantity()).isEqualTo(450_000L);
        assertThat(detail.priceChange()).isEqualByComparingTo("14.00");
        assertThat(detail.percentChange()).isEqualByComparingTo("1.38");
        assertThat(detail.finalPrice()).isEqualByComparingTo("1030.00");
        assertThat(detail.finalVolume()).isEqualTo(573_581L);
        assertThat(detail.tradedValue()).isEqualByComparingTo("590788430.00");
        assertThat(detail.ffmCap()).isEqualByComparingTo("250000.55");
        assertThat(detail.indicativeImbalanceQuantityAtEquilibriumPrice()).isEqualTo(1_200L);
        assertThat(detail.indicativeImbalanceQuantityAtMarketOrder()).isNull();
        assertThat(detail.week52High()).isEqualByComparingTo("1100.00");
        assertThat(detail.week52Low()).isEqualByComparingTo("780.25");
    }

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
