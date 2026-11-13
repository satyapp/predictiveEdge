package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageOneAnalysisBundleTest {
    @Test
    void rejectsStageOneOutputCreatedAfterTheEvidenceWasFrozen() {
        assertThatThrownBy(() -> new StageOneAnalysisBundle(
                UUID.randomUUID(), UUID.randomUUID(), "pre_open", "sha256:abc",
                Instant.parse("2026-11-11T03:40:00Z"), "ollama:qwen3",
                Instant.parse("2026-11-11T03:40:01Z"), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot follow");
    }
}
