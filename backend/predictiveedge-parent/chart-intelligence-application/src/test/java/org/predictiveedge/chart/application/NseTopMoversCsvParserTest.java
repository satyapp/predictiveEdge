package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.MarketScreenType;

class NseTopMoversCsvParserTest {
    private static final String HEADER = "SYMBOL,OPEN,HIGH,LOW,PREV. CLOSE,LTP,%CHANGE,VOLUME,VALUE,CA \n";

    @Test
    void parsesTheNseTopGainersSchemaAndPreservesCorporateAction() {
        String csv = HEADER
                + "INDIGO,5520.4,5692.3,5520,5430,5686.4,4.72,662569,3740685680.37,13-Aug-2025\n"
                + "M&M,3340,3424.4,3339.9,3290.6,3415,3.78,1594549,5406924313.12,03-Jul-2026\n";

        var result = new NseTopMoversCsvParser().parse(MarketScreenType.TOP_GAINERS, csv,
                Instant.parse("2026-11-11T10:00:00Z"));

        assertThat(result.rejectedRows()).isEmpty();
        assertThat(result.observations()).hasSize(2);
        assertThat(result.observations().getFirst()).satisfies(value -> {
            assertThat(value.symbol()).isEqualTo("INDIGO");
            assertThat(value.percentChange()).isEqualByComparingTo("4.72");
            assertThat(value.volume()).isEqualTo(662_569);
            assertThat(value.turnover()).isEqualByComparingTo("3740685680.37");
            assertThat(value.corporateAction()).isEqualTo("13-Aug-2025");
        });
    }

    @Test
    void parsesTheUtf8BomAndQuotedHeadersUsedByTheNseDownload() {
        String csv = "\uFEFF\"SYMBOL\", \"OPEN\",\"HIGH\",\"LOW\",\"PREV. CLOSE\",\"LTP\",\"%CHANGE\",\"VOLUME\",\"VALUE\",\"CA \"\r\n"
                + "\"INDIGO\",5520.4,5692.3,5520,5430,5686.4,4.72,662569,3740685680.37,\"13-Aug-2025\"\r\n";

        var result = new NseTopMoversCsvParser().parse(MarketScreenType.TOP_GAINERS, csv,
                Instant.parse("2026-11-11T10:00:00Z"));

        assertThat(result.rejectedRows()).isEmpty();
        assertThat(result.observations()).singleElement().satisfies(value -> {
            assertThat(value.symbol()).isEqualTo("INDIGO");
            assertThat(value.corporateAction()).isEqualTo("13-Aug-2025");
        });
    }

    @Test
    void rejectsRowsWhoseDirectionConflictsWithTheDeclaredScreen() {
        String csv = HEADER + "WRONG,100,101,99,100,100.5,-1.2,1000,100500,-\n";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NseTopMoversCsvParser().parse(
                MarketScreenType.TOP_GAINERS, csv, Instant.parse("2026-11-11T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no usable rows");
    }
}
