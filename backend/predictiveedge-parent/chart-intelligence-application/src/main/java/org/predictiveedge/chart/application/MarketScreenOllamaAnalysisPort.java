package org.predictiveedge.chart.application;

import java.util.UUID;

public interface MarketScreenOllamaAnalysisPort {
    StageOneAiResult analyze(UUID userId, String brokerAccountId,
            MarketScreenHistoricalAnalysisService.Result result);
}
