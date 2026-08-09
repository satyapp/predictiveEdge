package org.predictiveedge.marketintelligence.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL point-in-time reader that selects the newest causally eligible revision per interval. */
public final class JdbcMarketBarQueryAdapter implements MarketBarQueryPort {
    private static final String COLUMNS = """
            subject_type,subject_id,venue,trading_date,session_code,timeframe,interval_start,interval_end,
            is_truncated,revision,open_price,high_price,low_price,close_price,volume,observed_through,
            finality_state,available_at,correction_reason,input_manifest_hash,aggregation_policy_version,
            finality_policy_version
            """;
    private final JdbcTemplate jdbc;

    public JdbcMarketBarQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    public Optional<MarketBarRevision> findLatest(
            UUID userId, String brokerAccountId, ObservationSubject subject,
            BarTimeframe timeframe, EvaluationCutoff cutoff) {
        Objects.requireNonNull(userId, "User id is required");
        List<MarketBarRevision> values = jdbc.query("""
                select %s
                from market_intelligence.market_bar_revision
                where user_id=? and broker_account_id=? and subject_type=? and subject_id=? and timeframe=?
                  and interval_end<=? and available_at<=? and finality_state in ('FINAL','CORRECTED')
                order by interval_end desc,available_at desc,revision desc
                limit 1
                """.formatted(COLUMNS), this::mapRevision, userId, brokerAccountId, subject.type().name(),
                subject.id(), timeframe.name(), Timestamp.from(cutoff.analysisCutoff()),
                Timestamp.from(cutoff.knowledgeCutoff()));
        return values.stream().findFirst();
    }

    @Override
    public List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults) {
        if (maximumResults < 1) throw new IllegalArgumentException("Maximum replay results must be positive");
        var sql = new StringBuilder("""
                select %s from (
                  select %s,
                         row_number() over (
                           partition by venue,trading_date,session_code,interval_start
                           order by revision desc) as causal_rank
                  from market_intelligence.market_bar_revision
                  where user_id=? and broker_account_id=? and subject_type=? and subject_id=? and timeframe=?
                    and interval_start>=? and interval_end<=? and interval_end<=? and available_at<=?
                    and finality_state in ('FINAL','CORRECTED')
                """.formatted(COLUMNS, COLUMNS));
        var arguments = new ArrayList<Object>(List.of(criteria.userId(), criteria.brokerAccountId(),
                criteria.subject().type().name(), criteria.subject().id(), criteria.timeframe().name(),
                Timestamp.from(criteria.fromInclusive()), Timestamp.from(criteria.toExclusive()),
                Timestamp.from(criteria.cutoff().analysisCutoff()), Timestamp.from(criteria.cutoff().knowledgeCutoff())));
        if (criteria.after() != null) {
            sql.append("""
                      and (interval_start,venue,trading_date,session_code)>(?,?,?,?)
                    """);
            arguments.add(Timestamp.from(criteria.after().intervalStart()));
            arguments.add(criteria.after().venue());
            arguments.add(criteria.after().tradingDate());
            arguments.add(criteria.after().sessionCode());
        }
        sql.append("""
                ) eligible
                where causal_rank=1
                order by interval_start,venue,trading_date,session_code
                limit ?
                """);
        arguments.add(maximumResults);
        return jdbc.query(sql.toString(), this::mapRevision, arguments.toArray());
    }

    private MarketBarRevision mapRevision(ResultSet result, int rowNumber) throws SQLException {
        var subject = new ObservationSubject(
                org.predictiveedge.marketintelligence.domain.ObservationSubjectType.valueOf(
                        result.getString("subject_type")), result.getString("subject_id"));
        var session = new MarketSessionId(result.getString("venue"),
                result.getDate("trading_date").toLocalDate(), result.getString("session_code"));
        var interval = new BarInterval(result.getTimestamp("interval_start").toInstant(),
                result.getTimestamp("interval_end").toInstant(), result.getBoolean("is_truncated"));
        var key = new MarketBarKey(subject, session, BarTimeframe.valueOf(result.getString("timeframe")), interval);
        var values = new MarketBarValues(result.getBigDecimal("open_price"), result.getBigDecimal("high_price"),
                result.getBigDecimal("low_price"), result.getBigDecimal("close_price"), result.getLong("volume"));
        return new MarketBarRevision(key, result.getLong("revision"), values,
                result.getTimestamp("observed_through").toInstant(),
                BarFinalityState.valueOf(result.getString("finality_state")),
                result.getTimestamp("available_at").toInstant(), result.getString("correction_reason"),
                new ContentHash(result.getString("input_manifest_hash")),
                result.getString("aggregation_policy_version"), result.getString("finality_policy_version"));
    }
}
