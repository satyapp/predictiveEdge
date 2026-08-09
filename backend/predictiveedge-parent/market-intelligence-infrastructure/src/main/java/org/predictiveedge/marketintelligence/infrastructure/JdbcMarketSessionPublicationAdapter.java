package org.predictiveedge.marketintelligence.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.application.MarketSessionCalendarConflictException;
import org.predictiveedge.marketintelligence.application.MarketSessionDefinition;
import org.predictiveedge.marketintelligence.application.MarketSessionPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketSessionPublicationResult;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.SessionPhaseWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Serializes venue calendar publication and stores the definition and phases atomically. */
public class JdbcMarketSessionPublicationAdapter implements MarketSessionPublicationPort {
    private final JdbcTemplate jdbc;

    public JdbcMarketSessionPublicationAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    @Transactional
    public MarketSessionPublicationResult publish(MarketSessionDefinition definition) {
        Objects.requireNonNull(definition, "Session definition is required");
        String venue = definition.session().id().venue();
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", result -> null, venue);

        Optional<MarketSessionDefinition> existing = findById(definition.definitionId());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(definition))
                throw new MarketSessionCalendarConflictException(
                        "Session definition id is already assigned to different calendar content");
            return result(definition, MarketSessionPublicationResult.Status.ALREADY_PUBLISHED);
        }

        Integer conflicts = jdbc.queryForObject("""
                select count(*) from market_intelligence.market_session
                where venue=? and valid_from=? and coverage_start<? and coverage_end>?
                """, Integer.class, venue, Timestamp.from(definition.validFrom()),
                Timestamp.from(definition.coverageEnd()), Timestamp.from(definition.coverageStart()));
        if (conflicts != null && conflicts > 0)
            throw new MarketSessionCalendarConflictException(
                    "Another session definition is equally effective for an overlapping venue interval");

        var session = definition.session();
        var id = session.id();
        jdbc.update("""
                insert into market_intelligence.market_session (
                  session_definition_id,venue,trading_date,session_code,bar_anchor,session_end,
                  coverage_start,coverage_end,valid_from,valid_to,calendar_version)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, definition.definitionId(), id.venue(), id.tradingDate(), id.sessionCode(),
                Timestamp.from(session.barAnchor()), Timestamp.from(session.sessionEnd()),
                Timestamp.from(definition.coverageStart()), Timestamp.from(definition.coverageEnd()),
                Timestamp.from(definition.validFrom()), timestamp(definition.validTo()), session.calendarVersion());
        for (SessionPhaseWindow phase : session.phaseWindows()) {
            jdbc.update("""
                    insert into market_intelligence.market_session_phase (
                      session_definition_id,phase,starts_at,ends_at) values (?,?,?,?)
                    """, definition.definitionId(), phase.phase().name(), Timestamp.from(phase.startsAt()),
                    Timestamp.from(phase.endsAt()));
        }
        return result(definition, MarketSessionPublicationResult.Status.CREATED);
    }

    private Optional<MarketSessionDefinition> findById(UUID definitionId) {
        List<DefinitionRow> rows = jdbc.query("""
                select s.session_definition_id,s.venue,s.trading_date,s.session_code,s.bar_anchor,s.session_end,
                       s.coverage_start,s.coverage_end,s.valid_from,s.valid_to,s.calendar_version,
                       p.phase,p.starts_at,p.ends_at
                from market_intelligence.market_session s
                join market_intelligence.market_session_phase p
                  on p.session_definition_id=s.session_definition_id
                where s.session_definition_id=?
                order by p.starts_at
                """, this::mapRow, definitionId);
        if (rows.isEmpty()) return Optional.empty();
        DefinitionRow first = rows.getFirst();
        var session = new MarketSession(new MarketSessionId(first.venue(), first.tradingDate(), first.sessionCode()),
                first.barAnchor(), first.sessionEnd(), rows.stream().map(DefinitionRow::phase).toList(),
                first.calendarVersion());
        return Optional.of(new MarketSessionDefinition(first.definitionId(), session, first.coverageStart(),
                first.coverageEnd(), first.validFrom(), first.validTo()));
    }

    private DefinitionRow mapRow(ResultSet result, int rowNumber) throws SQLException {
        Timestamp validTo = result.getTimestamp("valid_to");
        return new DefinitionRow(result.getObject("session_definition_id", UUID.class), result.getString("venue"),
                result.getDate("trading_date").toLocalDate(), result.getString("session_code"),
                instant(result, "bar_anchor"), instant(result, "session_end"),
                instant(result, "coverage_start"), instant(result, "coverage_end"),
                instant(result, "valid_from"), validTo == null ? null : validTo.toInstant(),
                result.getString("calendar_version"), new SessionPhaseWindow(
                        MarketSessionPhase.valueOf(result.getString("phase")),
                        instant(result, "starts_at"), instant(result, "ends_at")));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static MarketSessionPublicationResult result(
            MarketSessionDefinition definition, MarketSessionPublicationResult.Status status) {
        return new MarketSessionPublicationResult(definition.definitionId(), status);
    }

    private record DefinitionRow(UUID definitionId, String venue, LocalDate tradingDate, String sessionCode,
                                 Instant barAnchor, Instant sessionEnd, Instant coverageStart, Instant coverageEnd,
                                 Instant validFrom, Instant validTo, String calendarVersion,
                                 SessionPhaseWindow phase) {}
}
