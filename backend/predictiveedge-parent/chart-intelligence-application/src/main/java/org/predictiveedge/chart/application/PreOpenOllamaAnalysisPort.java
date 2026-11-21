package org.predictiveedge.chart.application;

@FunctionalInterface
public interface PreOpenOllamaAnalysisPort {
    StageOneAiResult analyze(PreOpenCsvAnalysisService.Result deterministicResult);
}
