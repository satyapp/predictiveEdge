package org.predictiveedge.chart.application;

@FunctionalInterface
public interface PreOpenOllamaAnalysisPort {
    PreOpenOllamaAnalysis analyze(PreOpenCsvAnalysisService.Result deterministicResult);
}
