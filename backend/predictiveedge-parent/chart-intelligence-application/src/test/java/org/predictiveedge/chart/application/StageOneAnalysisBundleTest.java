package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageOneAnalysisBundleTest {
    @Test
    void exposesTheGoldenCopyAndStageOneOutputToTheFutureStageTwoPort() {
        Instant generatedAt = Instant.parse("2026-11-11T03:40:00Z");
        var analysis = new PreOpenOllamaAnalysis("qwen3:8b", generatedAt, true, "MIXED", "Overview",
                List.of(), List.of("Caution"));
        var goldenCopy = new StageOneGoldenCopy("stage-one-golden-copy-v1", "PRE_OPEN",
                generatedAt.plusSeconds(1), "abc", "{}", analysis, "PLANNED_PHASE_2");

        var bundle = new StageOneAnalysisBundle(UUID.randomUUID(), UUID.randomUUID(), goldenCopy);

        assertThat(bundle.stageOneAnalysis()).isEqualTo(analysis);
        assertThat(bundle.evidenceJson()).isEqualTo("{}");
        assertThat(bundle.sourceFingerprint()).isEqualTo("abc");
    }
}
