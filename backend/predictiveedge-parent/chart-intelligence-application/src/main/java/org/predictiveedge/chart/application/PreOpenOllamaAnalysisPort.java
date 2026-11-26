package org.predictiveedge.chart.application;

import java.util.UUID;

@FunctionalInterface
public interface PreOpenOllamaAnalysisPort {
    /**
     * Runs the Stage-1 analysis of the stocks selected from the pre-open shortlist. Each selected stock is analysed
     * with its persisted pre-open market data as its evidence. When {@code run} is not null, the selections, evidence
     * and analyses are saved under that run.
     */
    StageOneAiResult analyze(UUID userId, PreOpenCsvAnalysisService.Result deterministicResult,
            ScreenerRunRecorder.ScreenerRun run);
}
