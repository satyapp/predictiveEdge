package org.predictiveedge.chart.application;

/** Optional, user-triggered Stage-2 model boundary. No broker connector is exposed here. */
@FunctionalInterface
public interface AdvancedAnalysisPort {
    AdvancedAnalysisResult analyze(StageOneAnalysisBundle stageOne);
}
