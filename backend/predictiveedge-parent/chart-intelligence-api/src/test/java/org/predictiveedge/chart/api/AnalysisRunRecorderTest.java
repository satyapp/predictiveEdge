package org.predictiveedge.chart.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;

class AnalysisRunRecorderTest {
    @Test
    void persistsACompleteUserScopedReportWithSourceFingerprint() {
        AnalysisRunHistoryStore store = mock(AnalysisRunHistoryStore.class);
        Instant now = Instant.parse("2026-11-12T04:00:00Z");
        var recorder = new AnalysisRunRecorder(store, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        UUID runId = recorder.record(userId, "TOP_GAINERS", "MARKET_SCREEN", "gainers.csv",
                "SYMBOL,%CHANGE\nABC,4.2", LocalDate.of(2026, 11, 12), 1,
                "market-screen-v1", Map.of("authority", "IDEA_CANDIDATE_ONLY"));

        var captured = ArgumentCaptor.forClass(AnalysisRunHistoryStore.StoredAnalysisRun.class);
        verify(store).append(captured.capture());
        var saved = captured.getValue();
        assertThat(saved.runId()).isEqualTo(runId);
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.category()).isEqualTo("TOP_GAINERS");
        assertThat(saved.sourceSha256()).hasSize(64);
        assertThat(saved.createdAt()).isEqualTo(now);
        assertThat(saved.resultJson()).contains("IDEA_CANDIDATE_ONLY");
    }
}
