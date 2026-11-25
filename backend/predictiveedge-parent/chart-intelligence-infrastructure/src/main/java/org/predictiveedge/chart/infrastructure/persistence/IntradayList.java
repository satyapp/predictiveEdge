package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The intraday list handed to automation. Stage-1 lists have no parent. */
@Entity
@Table(name = "intraday_list", schema = "market_intelligence")
public class IntradayList {
    @Id
    @Column(name = "list_id", nullable = false)
    private UUID listId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stage", nullable = false)
    private String stage;

    @Column(name = "parent_list_id")
    private UUID parentListId;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected IntradayList() { }

    public IntradayList(UUID listId, UUID userId, String stage, UUID parentListId, int itemCount, Instant savedAt) {
        this.listId = listId;
        this.userId = userId;
        this.stage = stage;
        this.parentListId = parentListId;
        this.itemCount = itemCount;
        this.savedAt = savedAt;
    }
}
