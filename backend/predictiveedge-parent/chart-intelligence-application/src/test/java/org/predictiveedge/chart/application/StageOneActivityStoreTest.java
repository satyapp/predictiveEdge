package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.StageOneActivityStore.IntradayListSave;
import org.predictiveedge.chart.application.StageOneActivityStore.ScreenerRow;
import org.predictiveedge.chart.application.StageOneActivityStore.ScreenerSearch;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectedStockInput;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionBatch;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.chart.domain.MarketScreenType;

class StageOneActivityStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-25T03:45:00Z");

    @Test
    void screenerSearchRejectsTheSameSymbolTwice() {
        assertThatThrownBy(() -> new ScreenerSearch(UUID.randomUUID(), UUID.randomUUID(),
                SearchEvidenceCategory.TOP_GAINERS, "TOP_GAINERS", "", "", "", LocalDate.of(2026, 11, 25),
                "account-1", "{}", NOW, List.of(new ScreenerRow("infy", 1, "{}"), new ScreenerRow("INFY", 2, "{}"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same symbol twice");
    }

    @Test
    void screenerRowNormalizesSymbolAndRejectsNonPositiveRank() {
        var row = new ScreenerRow(" infy ", 1, "{\"symbol\":\"INFY\"}");

        assertThat(row.symbol()).isEqualTo("INFY");
        assertThatThrownBy(() -> new ScreenerRow("INFY", 0, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Screen rank");
    }

    @Test
    void selectionBatchNeedsAtLeastOneDistinctStock() {
        UUID searchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var analysis = new StageOneAnalysisInput("POSITIVE", "thesis", "confirm", "risk", "qwen3:8b", NOW, "{}");

        assertThatThrownBy(() -> new SelectionBatch(searchId, userId, SearchEvidenceCategory.GAP_UP, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectionBatch(searchId, userId, SearchEvidenceCategory.GAP_UP, NOW, List.of(
                new SelectedStockInput(UUID.randomUUID(), "tcs", "{}", analysis),
                new SelectedStockInput(UUID.randomUUID(), "TCS", "{}", analysis))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same symbol twice");
    }

    @Test
    void intradayListRejectsRepeatedSelections() {
        UUID selection = UUID.randomUUID();

        assertThatThrownBy(() -> new IntradayListSave(UUID.randomUUID(), UUID.randomUUID(), NOW,
                List.of(selection, selection)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeat a selection");
        assertThatThrownBy(() -> new IntradayListSave(UUID.randomUUID(), UUID.randomUUID(), NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
