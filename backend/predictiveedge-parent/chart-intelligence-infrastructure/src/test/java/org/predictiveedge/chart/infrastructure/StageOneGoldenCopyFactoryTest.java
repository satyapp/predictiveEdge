package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;

class StageOneGoldenCopyFactoryTest {
    @Test
    void freezesEvidenceTogetherWithTheCompleteStageOneAnalysis() {
        Instant generatedAt = Instant.parse("2026-11-14T06:00:00Z");
        Instant frozenAt = generatedAt.plusSeconds(1);
        var analysis = new PreOpenOllamaAnalysis("qwen3:8b", generatedAt, true, "MIXED", "Overview",
                List.of(), List.of("Caution"));

        var result = new StageOneGoldenCopyFactory(new ObjectMapper(),
                Clock.fixed(frozenAt, ZoneOffset.UTC)).freeze("PRE_OPEN", "{\"modules\":12}", analysis);

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.goldenCopy().stageOneAnalysis()).isEqualTo(analysis);
        assertThat(result.goldenCopy().stageTwoStatus()).isEqualTo("PLANNED_PHASE_2");
        assertThat(result.goldenCopy().sourceFingerprint()).hasSize(64);
    }
}
