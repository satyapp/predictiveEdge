package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies one evidence row: the selection, the stage and the provider source. */
@Embeddable
public class EvidenceKey implements Serializable {
    @Column(name = "selection_id", nullable = false)
    private UUID selectionId;
    @Column(name = "stage", nullable = false)
    private String stage;
    @Column(name = "source", nullable = false)
    private String source;

    protected EvidenceKey() { }

    public EvidenceKey(UUID selectionId, String stage, String source) {
        this.selectionId = Objects.requireNonNull(selectionId, "selectionId is required");
        this.stage = Objects.requireNonNull(stage, "stage is required");
        this.source = Objects.requireNonNull(source, "source is required");
    }

    public UUID selectionId() { return selectionId; }
    public String stage() { return stage; }
    public String source() { return source; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof EvidenceKey other)) return false;
        return Objects.equals(selectionId, other.selectionId) && Objects.equals(stage, other.stage) && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectionId, stage, source);
    }
}
