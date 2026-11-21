package org.predictiveedge.chart.application;

public interface MarketScreenOllamaAnalysisPort {
    StageOneAiResult analyze(MarketScreenHistoricalAnalysisService.Result result);
}
