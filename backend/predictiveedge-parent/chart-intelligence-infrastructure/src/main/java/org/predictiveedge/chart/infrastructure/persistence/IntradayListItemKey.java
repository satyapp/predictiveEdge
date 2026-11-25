package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies one intraday-list item: the list and the selection. */
@Embeddable
public class IntradayListItemKey implements Serializable {
    @Column(name = "list_id", nullable = false)
    private UUID listId;
    @Column(name = "selection_id", nullable = false)
    private UUID selectionId;

    protected IntradayListItemKey() { }

    public IntradayListItemKey(UUID listId, UUID selectionId) {
        this.listId = Objects.requireNonNull(listId, "listId is required");
        this.selectionId = Objects.requireNonNull(selectionId, "selectionId is required");
    }

    public UUID listId() { return listId; }
    public UUID selectionId() { return selectionId; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof IntradayListItemKey other)) return false;
        return Objects.equals(listId, other.listId) && Objects.equals(selectionId, other.selectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listId, selectionId);
    }
}
