package org.predictiveedge.marketintelligence.infrastructure;

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
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.ContextScopeType;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketContextKey;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcMarketContextStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsOnceAndReadsOnlyCausallyReadyMarketContexts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = mock(ObjectMapper.class);
        MarketContextSnapshot snapshot = mock(MarketContextSnapshot.class);
        MarketContextKey key = new MarketContextKey(ContextScopeType.INSTRUMENT, "INE002A01018", "INTRADAY");
        EvaluationCutoff snapshotCutoff = new EvaluationCutoff(NOW.minusSeconds(30), NOW.minusSeconds(20));
        when(snapshot.key()).thenReturn(key);
        when(snapshot.cutoff()).thenReturn(snapshotCutoff);
        when(snapshot.decisionReadyAt()).thenReturn(NOW.minusSeconds(10));
        when(snapshot.expiresAt()).thenReturn(NOW.plusSeconds(30));
        when(snapshot.semanticHash()).thenReturn(new ContentHash("c".repeat(64)));
        when(json.writeValueAsString(snapshot)).thenReturn("{}");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ResultSet result = mock(ResultSet.class);
        when(result.getString("snapshot_json")).thenReturn("{}");
        when(json.readValue("{}", MarketContextSnapshot.class)).thenReturn(snapshot);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("analysis_cutoff<=?", "knowledge_cutoff<=?", "decision_ready_at<=?");
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0));
        });
        var store = new JdbcMarketContextStore(jdbc, json);
        UUID userId = UUID.randomUUID();

        assertThat(store.append(userId, snapshot)).isTrue();
        assertThat(store.findLatest(userId, key, new EvaluationCutoff(NOW, NOW))).contains(snapshot);
    }
}
