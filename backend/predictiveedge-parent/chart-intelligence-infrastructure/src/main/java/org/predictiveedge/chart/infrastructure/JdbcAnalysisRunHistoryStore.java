package org.predictiveedge.chart.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL-backed, user-scoped analysis history. Full reports are stored as JSONB. */
public final class JdbcAnalysisRunHistoryStore implements AnalysisRunHistoryStore {
    private final JdbcTemplate jdbc;

    public JdbcAnalysisRunHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    public void append(StoredAnalysisRun run) {
        Objects.requireNonNull(run, "Analysis run is required");
        jdbc.update("""
                insert into chart_intelligence.analysis_run (
                  run_id,user_id,category,result_kind,file_name,source_sha256,trading_date,
                  candidate_count,stage_one_model,created_at,result_json)
                values (?,?,?,?,?,?,?,?,?,?,?::jsonb)
                """, run.runId(), run.userId(), run.category(), run.resultKind(), run.fileName(),
                run.sourceSha256(), run.tradingDate(), run.candidateCount(), run.stageOneModel(),
                Timestamp.from(run.createdAt()), run.resultJson());
    }

    @Override
    public List<AnalysisRunSummary> findRecent(UUID userId, int limit) {
        Objects.requireNonNull(userId, "User id is required");
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                select run_id,category,result_kind,file_name,source_sha256,trading_date,
                       candidate_count,stage_one_model,created_at
                from chart_intelligence.analysis_run
                where user_id=?
                order by created_at desc,run_id desc
                limit ?
                """, this::mapSummary, userId, boundedLimit);
    }

    @Override
    public Optional<StoredAnalysisRun> findById(UUID userId, UUID runId) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(runId, "Run id is required");
        return jdbc.query("""
                select run_id,user_id,category,result_kind,file_name,source_sha256,trading_date,
                       candidate_count,stage_one_model,created_at,result_json::text
                from chart_intelligence.analysis_run
                where user_id=? and run_id=?
                """, this::mapRun, userId, runId).stream().findFirst();
    }

    private AnalysisRunSummary mapSummary(ResultSet result, int rowNumber) throws SQLException {
        return new AnalysisRunSummary(result.getObject("run_id", UUID.class), result.getString("category"),
                result.getString("result_kind"), result.getString("file_name"),
                result.getString("source_sha256"), result.getObject("trading_date", java.time.LocalDate.class),
                result.getInt("candidate_count"), result.getString("stage_one_model"),
                result.getTimestamp("created_at").toInstant());
    }

    private StoredAnalysisRun mapRun(ResultSet result, int rowNumber) throws SQLException {
        return new StoredAnalysisRun(result.getObject("run_id", UUID.class),
                result.getObject("user_id", UUID.class), result.getString("category"),
                result.getString("result_kind"), result.getString("file_name"),
                result.getString("source_sha256"), result.getObject("trading_date", java.time.LocalDate.class),
                result.getInt("candidate_count"), result.getString("stage_one_model"),
                result.getTimestamp("created_at").toInstant(), result.getString("result_json"));
    }
}
