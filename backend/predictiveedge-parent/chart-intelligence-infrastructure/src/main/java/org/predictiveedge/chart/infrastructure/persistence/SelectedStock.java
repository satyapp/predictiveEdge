package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A stock the user chose from one screener run for Stage-1 analysis. */
@Entity
@Table(name = "selected_stock", schema = "market_intelligence")
public class SelectedStock {
    @Id
    @Column(name = "selection_id", nullable = false)
    private UUID selectionId;

    @Column(name = "search_id", nullable = false)
    private UUID searchId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "stage", nullable = false)
    private String stage;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    protected SelectedStock() { }

    public SelectedStock(UUID selectionId, UUID searchId, String category, UUID userId, String symbol,
            String stage, Instant selectedAt) {
        this.selectionId = selectionId;
        this.searchId = searchId;
        this.category = category;
        this.userId = userId;
        this.symbol = symbol;
        this.stage = stage;
        this.selectedAt = selectedAt;
    }

    public UUID getSelectionId() { return selectionId; }
    public UUID getUserId() { return userId; }
    public String getSymbol() { return symbol; }
}
