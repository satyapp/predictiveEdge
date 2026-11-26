package org.predictiveedge.chart.application;

import java.util.UUID;

public interface MarketScreenOllamaAnalysisPort {
    /**
     * Runs the Stage-1 analysis. When {@code run} is not null, the selected stocks, their evidence and their analysis
     * are saved under that screener run. When it is null, nothing is saved.
     */
    StageOneAiResult analyze(UUID userId, String brokerAccountId,
            MarketScreenHistoricalAnalysisService.Result result, ScreenerRunRecorder.ScreenerRun run);
}
