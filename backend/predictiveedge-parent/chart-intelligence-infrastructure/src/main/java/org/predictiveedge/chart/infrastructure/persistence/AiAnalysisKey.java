package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies one AI analysis: the selection and the stage. */
@Embeddable
public class AiAnalysisKey implements Serializable {
    @Column(name = "selection_id", nullable = false)
    private UUID selectionId;
    @Column(name = "stage", nullable = false)
    private String stage;

    protected AiAnalysisKey() { }

    public AiAnalysisKey(UUID selectionId, String stage) {
        this.selectionId = Objects.requireNonNull(selectionId, "selectionId is required");
        this.stage = Objects.requireNonNull(stage, "stage is required");
    }

    public UUID selectionId() { return selectionId; }
    public String stage() { return stage; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof AiAnalysisKey other)) return false;
        return Objects.equals(selectionId, other.selectionId) && Objects.equals(stage, other.stage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectionId, stage);
    }
}
