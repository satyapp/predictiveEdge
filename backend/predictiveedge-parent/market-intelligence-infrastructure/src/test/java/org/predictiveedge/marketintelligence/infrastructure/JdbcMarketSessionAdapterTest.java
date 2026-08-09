package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcMarketSessionAdapterTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reconstructsTheEffectiveSessionAndItsPhaseWindows() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet result = mock(ResultSet.class);
        Instant open = Instant.parse("2026-08-07T03:45:00Z");
        Instant close = Instant.parse("2026-08-07T10:00:00Z");
        when(result.getString("venue")).thenReturn("NSE");
        when(result.getDate("trading_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 8, 7)));
        when(result.getString("session_code")).thenReturn("REGULAR");
        when(result.getTimestamp("bar_anchor")).thenReturn(Timestamp.from(open));
        when(result.getTimestamp("session_end")).thenReturn(Timestamp.from(close));
        when(result.getString("calendar_version")).thenReturn("nse-v1");
        when(result.getString("phase")).thenReturn("CONTINUOUS");
        when(result.getTimestamp("starts_at")).thenReturn(Timestamp.from(open));
        when(result.getTimestamp("ends_at")).thenReturn(Timestamp.from(close));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0));
        });

        var session = new JdbcMarketSessionAdapter(jdbc)
                .sessionFor(new Instrument("NSE", "INFY"), open.plusSeconds(1)).orElseThrow();

        assertThat(session.id().venue()).isEqualTo("NSE");
        assertThat(session.calendarVersion()).isEqualTo("nse-v1");
        assertThat(session.phaseAt(open)).contains(MarketSessionPhase.CONTINUOUS);
    }
}
