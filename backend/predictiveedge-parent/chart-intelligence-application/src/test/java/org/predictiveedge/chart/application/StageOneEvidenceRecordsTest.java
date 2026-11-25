package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.StageOneActivityStore.EvidenceSave;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionStart;
import org.predictiveedge.chart.application.StageOneActivityStore.StockSelection;

class StageOneEvidenceRecordsTest {
    private static final Instant NOW = Instant.parse("2026-11-25T03:45:00Z");

    @Test
    void selectionStartNormalizesSymbolsAndRejectsDuplicates() {
        var start = new SelectionStart(UUID.randomUUID(), UUID.randomUUID(), SearchEvidenceCategory.GAP_UP, NOW,
                List.of(new StockSelection(UUID.randomUUID(), " infy ")));

        assertThat(start.stocks().get(0).symbol()).isEqualTo("INFY");
        assertThatThrownBy(() -> new SelectionStart(UUID.randomUUID(), UUID.randomUUID(),
                SearchEvidenceCategory.GAP_UP, NOW, List.of(new StockSelection(UUID.randomUUID(), "TCS"),
                        new StockSelection(UUID.randomUUID(), "tcs"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same symbol twice");
    }

    @Test
    void selectionStartNeedsAtLeastOneStockAndDistinctSelectionIds() {
        UUID selectionId = UUID.randomUUID();

        assertThatThrownBy(() -> new SelectionStart(UUID.randomUUID(), UUID.randomUUID(),
                SearchEvidenceCategory.GAP_UP, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one stock");
        assertThatThrownBy(() -> new SelectionStart(UUID.randomUUID(), UUID.randomUUID(),
                SearchEvidenceCategory.GAP_UP, NOW, List.of(new StockSelection(selectionId, "TCS"),
                        new StockSelection(selectionId, "INFY"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selection ids must be distinct");
    }

    @Test
    void evidenceSaveRequiresASelectionDomainAndJson() {
        var save = new EvidenceSave(UUID.randomUUID(), "MARKET_DATA", "[]");

        assertThat(save.domain()).isEqualTo("MARKET_DATA");
        assertThatThrownBy(() -> new EvidenceSave(null, "MARKET_DATA", "[]"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvidenceSave(UUID.randomUUID(), " ", "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evidence domain");
        assertThatThrownBy(() -> new EvidenceSave(UUID.randomUUID(), "MARKET_DATA", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evidence JSON");
    }
}
