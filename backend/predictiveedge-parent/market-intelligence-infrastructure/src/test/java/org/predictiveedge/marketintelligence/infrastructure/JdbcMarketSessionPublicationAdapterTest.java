package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.application.MarketSessionCalendarConflictException;
import org.predictiveedge.marketintelligence.application.MarketSessionDefinition;
import org.predictiveedge.marketintelligence.application.MarketSessionPublicationResult;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.SessionPhaseWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcMarketSessionPublicationAdapterTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void atomicallyInsertsTheSessionAndAllOrderedPhases() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(0);

        var result = new JdbcMarketSessionPublicationAdapter(jdbc).publish(definition());

        assertThat(result.status()).isEqualTo(MarketSessionPublicationResult.Status.CREATED);
        verify(jdbc, times(4)).update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsAnEquallyEffectiveOverlappingVenueDefinition() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> new JdbcMarketSessionPublicationAdapter(jdbc).publish(definition()))
                .isInstanceOf(MarketSessionCalendarConflictException.class)
                .hasMessageContaining("equally effective");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void treatsAnIdenticalDefinitionIdAndContentAsAnIdempotentRetry() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MarketSessionDefinition definition = definition();
        ResultSet result = mock(ResultSet.class);
        var session = definition.session();
        when(result.getObject("session_definition_id", UUID.class)).thenReturn(definition.definitionId());
        when(result.getString("venue")).thenReturn(session.id().venue());
        when(result.getDate("trading_date")).thenReturn(Date.valueOf(session.id().tradingDate()));
        when(result.getString("session_code")).thenReturn(session.id().sessionCode());
        when(result.getTimestamp("bar_anchor")).thenReturn(Timestamp.from(session.barAnchor()));
        when(result.getTimestamp("session_end")).thenReturn(Timestamp.from(session.sessionEnd()));
        when(result.getTimestamp("coverage_start")).thenReturn(Timestamp.from(definition.coverageStart()));
        when(result.getTimestamp("coverage_end")).thenReturn(Timestamp.from(definition.coverageEnd()));
        when(result.getTimestamp("valid_from")).thenReturn(Timestamp.from(definition.validFrom()));
        when(result.getString("calendar_version")).thenReturn(session.calendarVersion());
        when(result.getString("phase")).thenReturn("PRE_OPEN", "CONTINUOUS", "CLOSED");
        when(result.getTimestamp("starts_at")).thenReturn(
                Timestamp.from(session.phaseWindows().get(0).startsAt()),
                Timestamp.from(session.phaseWindows().get(1).startsAt()),
                Timestamp.from(session.phaseWindows().get(2).startsAt()));
        when(result.getTimestamp("ends_at")).thenReturn(
                Timestamp.from(session.phaseWindows().get(0).endsAt()),
                Timestamp.from(session.phaseWindows().get(1).endsAt()),
                Timestamp.from(session.phaseWindows().get(2).endsAt()));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0), mapper.mapRow(result, 1), mapper.mapRow(result, 2));
        });

        var publication = new JdbcMarketSessionPublicationAdapter(jdbc).publish(definition);

        assertThat(publication.status()).isEqualTo(MarketSessionPublicationResult.Status.ALREADY_PUBLISHED);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static MarketSessionDefinition definition() {
        Instant coverageStart = Instant.parse("2026-08-07T03:30:00Z");
        Instant open = Instant.parse("2026-08-07T03:45:00Z");
        Instant close = Instant.parse("2026-08-07T10:00:00Z");
        Instant coverageEnd = Instant.parse("2026-08-07T10:15:00Z");
        var session = new MarketSession(new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"),
                open, close, List.of(
                new SessionPhaseWindow(MarketSessionPhase.PRE_OPEN, coverageStart, open),
                new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS, open, close),
                new SessionPhaseWindow(MarketSessionPhase.CLOSED, close, coverageEnd)), "nse-v1");
        return new MarketSessionDefinition(UUID.randomUUID(), session, coverageStart, coverageEnd,
                coverageStart.minusSeconds(3600), null);
    }
}
