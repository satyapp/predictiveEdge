package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Shared columns of the nineteen per-category screener tables. Each category maps its own table. */
@MappedSuperclass
public abstract class SearchResultStockRow {
    @EmbeddedId
    private SearchStockKey key;

    @Column(name = "screen_rank", nullable = false)
    private int screenRank;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "broker_row", nullable = false)
    private String brokerRow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SearchResultStockRow() { }

    public SearchStockKey key() { return key; }
    public int screenRank() { return screenRank; }
    public String brokerRow() { return brokerRow; }
    public Instant createdAt() { return createdAt; }

    protected SearchResultStockRow(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt) {
        this.key = key;
        this.screenRank = screenRank;
        this.brokerRow = brokerRow;
        this.createdAt = createdAt;
    }
}
