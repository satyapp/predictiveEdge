package org.predictiveedge.marketintelligence.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.marketintelligence.application.MarketSessionPort;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.SessionPhaseWindow;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the one effective venue-session definition covering an exchange timestamp. */
public final class JdbcMarketSessionAdapter implements MarketSessionPort {
    private final JdbcTemplate jdbc;

    public JdbcMarketSessionAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    public Optional<MarketSession> sessionFor(Instrument instrument, Instant exchangeTimestamp) {
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(exchangeTimestamp, "Exchange timestamp is required");
        Timestamp at = Timestamp.from(exchangeTimestamp);
        List<SessionRow> rows = jdbc.query("""
                select s.venue,s.trading_date,s.session_code,s.bar_anchor,s.session_end,s.calendar_version,
                       p.phase,p.starts_at,p.ends_at
                from market_intelligence.market_session s
                join market_intelligence.market_session_phase p
                  on p.session_definition_id=s.session_definition_id
                where s.session_definition_id=(
                  select candidate.session_definition_id
                  from market_intelligence.market_session candidate
                  where candidate.venue=? and candidate.coverage_start<=? and candidate.coverage_end>?
                    and candidate.valid_from<=? and (candidate.valid_to is null or candidate.valid_to>?)
                  order by candidate.valid_from desc
                  limit 1
                )
                order by p.starts_at
                """, this::mapRow, instrument.exchange(), at, at, at, at);
        if (rows.isEmpty()) return Optional.empty();
        SessionRow first = rows.getFirst();
        return Optional.of(new MarketSession(
                new MarketSessionId(first.venue(), first.tradingDate(), first.sessionCode()),
                first.barAnchor(), first.sessionEnd(), rows.stream().map(SessionRow::window).toList(),
                first.calendarVersion()));
    }

    private SessionRow mapRow(ResultSet result, int rowNumber) throws SQLException {
        return new SessionRow(result.getString("venue"), result.getDate("trading_date").toLocalDate(),
                result.getString("session_code"), result.getTimestamp("bar_anchor").toInstant(),
                result.getTimestamp("session_end").toInstant(), result.getString("calendar_version"),
                new SessionPhaseWindow(MarketSessionPhase.valueOf(result.getString("phase")),
                        result.getTimestamp("starts_at").toInstant(), result.getTimestamp("ends_at").toInstant()));
    }

    private record SessionRow(String venue, LocalDate tradingDate, String sessionCode,
                              Instant barAnchor, Instant sessionEnd, String calendarVersion,
                              SessionPhaseWindow window) {}
}
