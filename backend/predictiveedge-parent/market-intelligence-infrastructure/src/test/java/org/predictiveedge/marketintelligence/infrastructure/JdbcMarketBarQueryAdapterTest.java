package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcMarketBarQueryAdapterTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reconstructsCausalRevisionsForTenantScopedReplay() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet result = resultSet();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);
            assertThat(sql).contains("row_number() over", "user_id=?", "available_at<=?");
            return List.of(mapper.mapRow(result, 0));
        });
        Instant start = Instant.parse("2026-08-07T03:45:00Z");
        var criteria = new MarketBarReplayCriteria(UUID.randomUUID(), "ZD123",
                new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:INFY"),
                BarTimeframe.ONE_MINUTE, start, start.plusSeconds(600),
                new EvaluationCutoff(start.plusSeconds(600), start.plusSeconds(700)), null);

        var revisions = new JdbcMarketBarQueryAdapter(jdbc).replay(criteria, 101);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.getFirst().revision()).isEqualTo(2);
        assertThat(revisions.getFirst().finalityState()).isEqualTo(BarFinalityState.CORRECTED);
        assertThat(revisions.getFirst().values().close()).isEqualByComparingTo("105");
    }

    private static ResultSet resultSet() throws Exception {
        ResultSet result = mock(ResultSet.class);
        Instant start = Instant.parse("2026-08-07T03:45:00Z");
        when(result.getString("subject_type")).thenReturn("INSTRUMENT");
        when(result.getString("subject_id")).thenReturn("NSE:INFY");
        when(result.getString("venue")).thenReturn("NSE");
        when(result.getDate("trading_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 8, 7)));
        when(result.getString("session_code")).thenReturn("REGULAR");
        when(result.getString("timeframe")).thenReturn("ONE_MINUTE");
        when(result.getTimestamp("interval_start")).thenReturn(Timestamp.from(start));
        when(result.getTimestamp("interval_end")).thenReturn(Timestamp.from(start.plusSeconds(60)));
        when(result.getLong("revision")).thenReturn(2L);
        when(result.getBigDecimal("open_price")).thenReturn(new BigDecimal("100"));
        when(result.getBigDecimal("high_price")).thenReturn(new BigDecimal("110"));
        when(result.getBigDecimal("low_price")).thenReturn(new BigDecimal("99"));
        when(result.getBigDecimal("close_price")).thenReturn(new BigDecimal("105"));
        when(result.getLong("volume")).thenReturn(15L);
        when(result.getTimestamp("observed_through")).thenReturn(Timestamp.from(start.plusSeconds(50)));
        when(result.getString("finality_state")).thenReturn("CORRECTED");
        when(result.getTimestamp("available_at")).thenReturn(Timestamp.from(start.plusSeconds(65)));
        when(result.getString("correction_reason")).thenReturn("LATE_OR_OUT_OF_ORDER_TICK");
        when(result.getString("input_manifest_hash")).thenReturn("a".repeat(64));
        when(result.getString("aggregation_policy_version")).thenReturn("tick-v1");
        when(result.getString("finality_policy_version")).thenReturn("finality-v1");
        return result;
    }
}
