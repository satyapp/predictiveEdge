package org.predictiveedge.chart.application;

import java.util.Objects;

/** Stage-1 response paired with the exact persisted input/output golden copy for future Stage-2. */
public record StageOneAiResult(PreOpenOllamaAnalysis analysis, StageOneGoldenCopy goldenCopy) {
    public StageOneAiResult {
        Objects.requireNonNull(analysis, "Stage-1 analysis is required");
        Objects.requireNonNull(goldenCopy, "Stage-1 golden copy is required");
        if (!analysis.equals(goldenCopy.stageOneAnalysis()))
            throw new IllegalArgumentException("Golden copy must contain the returned Stage-1 analysis");
    }
}
