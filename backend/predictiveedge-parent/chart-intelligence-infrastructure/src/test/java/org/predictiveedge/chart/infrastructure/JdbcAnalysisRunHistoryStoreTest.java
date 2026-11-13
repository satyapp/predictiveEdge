package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore.StoredAnalysisRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcAnalysisRunHistoryStoreTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsAndReadsOnlyWithinTheOwningUserScope() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-12T04:00:00Z");
        var run = new StoredAnalysisRun(runId, userId, "TOP_GAINERS", "MARKET_SCREEN", "gainers.csv",
                "a".repeat(64), LocalDate.of(2026, 11, 12), 10, "market-screen-v1", createdAt, "{}");
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("run_id", UUID.class)).thenReturn(runId);
        when(result.getObject("user_id", UUID.class)).thenReturn(userId);
        when(result.getString("category")).thenReturn("TOP_GAINERS");
        when(result.getString("result_kind")).thenReturn("MARKET_SCREEN");
        when(result.getString("file_name")).thenReturn("gainers.csv");
        when(result.getString("source_sha256")).thenReturn("a".repeat(64));
        when(result.getObject("trading_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 11, 12));
        when(result.getInt("candidate_count")).thenReturn(10);
        when(result.getString("stage_one_model")).thenReturn("market-screen-v1");
        when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(result.getString("result_json")).thenReturn("{}");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("where user_id=? and run_id=?");
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0));
        });
        var store = new JdbcAnalysisRunHistoryStore(jdbc);

        store.append(run);
        assertThat(store.findById(userId, runId)).contains(run);
        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
