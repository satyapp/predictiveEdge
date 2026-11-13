package org.predictiveedge.chart.application;

public interface MarketScreenOllamaAnalysisPort {
    PreOpenOllamaAnalysis analyze(MarketScreenHistoricalAnalysisService.Result result);
}
