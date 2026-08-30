package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.ChartBias;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.chart.domain.ChartTrigger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcChartSnapshotStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsOnceAndReadsOnlyCausallyAvailableSnapshots() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = mock(ObjectMapper.class);
        ChartSnapshot snapshot = snapshot();
        when(json.writeValueAsString(snapshot)).thenReturn("{}");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ResultSet result = mock(ResultSet.class);
        when(result.getString("snapshot_json")).thenReturn("{}");
        when(json.readValue("{}", ChartSnapshot.class)).thenReturn(snapshot);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("analysis_cutoff<=?", "knowledge_cutoff<=?", "available_at<=?");
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0));
        });
        var store = new JdbcChartSnapshotStore(jdbc, json);
        UUID userId = UUID.randomUUID();

        assertThat(store.append(userId, snapshot)).isTrue();
        assertThat(store.findLatest(userId, "nse", "ine002a01018", NOW)).contains(snapshot);
    }

    private static ChartSnapshot snapshot() {
        return new ChartSnapshot("chart-1", "NSE", "INE002A01018", ChartBias.BULLISH, ChartBias.NEUTRAL,
                ChartBias.BULLISH, ChartTrigger.UPSIDE_BREAKOUT, true, true, true, 82,
                ChartReadiness.READY, true, NOW.minusSeconds(30), NOW.minusSeconds(20), NOW.minusSeconds(10),
                NOW.plusSeconds(30), "b".repeat(64), List.of("market-context:1"));
    }
}
