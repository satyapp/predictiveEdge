package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.predictiveedge.decision.application.ExecutionContextQuery;
import org.predictiveedge.decision.application.ExecutionEvidencePublicationPort;
import org.predictiveedge.decision.application.ExecutionEvidenceQueryPort;
import org.predictiveedge.decision.application.PortfolioSnapshotPublicationPort;
import org.predictiveedge.decision.application.PortfolioSnapshotQueryPort;
import org.predictiveedge.decision.application.RiskSnapshotPublicationPort;
import org.predictiveedge.decision.application.RiskSnapshotQueryPort;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;
import org.springframework.jdbc.core.JdbcTemplate;

/** Append-only factual stores for the three mandatory safety resources. */
public final class JdbcDecisionSafetySnapshotStore implements
        RiskSnapshotPublicationPort, RiskSnapshotQueryPort,
        PortfolioSnapshotPublicationPort, PortfolioSnapshotQueryPort,
        ExecutionEvidencePublicationPort, ExecutionEvidenceQueryPort, ExecutionContextQuery {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcDecisionSafetySnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    public boolean append(RiskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Risk snapshot is required");
        return jdbc.update("""
                insert into decision.risk_snapshot (
                  snapshot_id,user_id,venue,instrument_id,analysis_cutoff,knowledge_cutoff,
                  available_at,valid_until,evidence_hash,snapshot_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, snapshot.snapshotId(), snapshot.userId(), snapshot.instrument().venue(),
                snapshot.instrument().instrumentId(), Timestamp.from(snapshot.analysisCutoff()),
                Timestamp.from(snapshot.knowledgeCutoff()), Timestamp.from(snapshot.availableAt()),
                Timestamp.from(snapshot.validUntil()), snapshot.evidenceHash(), serialize(snapshot)) == 1;
    }

    @Override
    public Optional<RiskSnapshot> findLatestRisk(ShadowScope scope, Instant cutoff) {
        return query("decision.risk_snapshot", scope, cutoff, RiskSnapshot.class);
    }

    @Override
    public boolean append(PortfolioSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Portfolio snapshot is required");
        return jdbc.update("""
                insert into decision.portfolio_snapshot (
                  snapshot_id,user_id,venue,instrument_id,analysis_cutoff,knowledge_cutoff,
                  available_at,valid_until,evidence_hash,snapshot_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, snapshot.snapshotId(), snapshot.userId(), snapshot.instrument().venue(),
                snapshot.instrument().instrumentId(), Timestamp.from(snapshot.analysisCutoff()),
                Timestamp.from(snapshot.knowledgeCutoff()), Timestamp.from(snapshot.availableAt()),
                Timestamp.from(snapshot.validUntil()), snapshot.evidenceHash(), serialize(snapshot)) == 1;
    }

    @Override
    public Optional<PortfolioSnapshot> findLatestPortfolio(ShadowScope scope, Instant cutoff) {
        return query("decision.portfolio_snapshot", scope, cutoff, PortfolioSnapshot.class);
    }

    @Override
    public boolean append(ExecutionEvidenceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Execution evidence snapshot is required");
        return jdbc.update("""
                insert into decision.execution_snapshot (
                  snapshot_id,user_id,venue,instrument_id,analysis_cutoff,knowledge_cutoff,
                  available_at,valid_until,evidence_hash,snapshot_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, snapshot.snapshotId(), snapshot.userId(), snapshot.instrument().venue(),
                snapshot.instrument().instrumentId(), Timestamp.from(snapshot.analysisCutoff()),
                Timestamp.from(snapshot.knowledgeCutoff()), Timestamp.from(snapshot.availableAt()),
                Timestamp.from(snapshot.context().validUntil()), snapshot.evidenceHash(), serialize(snapshot)) == 1;
    }

    @Override
    public Optional<ExecutionEvidenceSnapshot> findLatestEvidence(ShadowScope scope, Instant cutoff) {
        return query("decision.execution_snapshot", scope, cutoff, ExecutionEvidenceSnapshot.class);
    }

    @Override
    public ExecutionContext findLatest(ShadowScope scope, Instant cutoff) {
        return findLatestEvidence(scope, cutoff).map(ExecutionEvidenceSnapshot::context)
                .orElseThrow(() -> new IllegalStateException("No causal execution context exists for shadow evidence"));
    }

    private <T> Optional<T> query(String table, ShadowScope scope, Instant cutoff, Class<T> type) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(cutoff, "Evidence cutoff is required");
        String sql = """
                select snapshot_json::text
                from %s
                where user_id=? and venue=? and instrument_id=?
                  and analysis_cutoff<=? and knowledge_cutoff<=? and available_at<=?
                order by analysis_cutoff desc,knowledge_cutoff desc,available_at desc
                limit 1
                """.formatted(table);
        return jdbc.query(sql, (result, rowNumber) -> deserialize(result, type), scope.userId(),
                scope.instrument().venue(), scope.instrument().instrumentId(), Timestamp.from(cutoff),
                Timestamp.from(cutoff), Timestamp.from(cutoff)).stream().findFirst();
    }

    private <T> T deserialize(ResultSet result, Class<T> type) throws SQLException {
        try {
            return json.readValue(result.getString("snapshot_json"), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored " + type.getSimpleName() + " is invalid", exception);
        }
    }

    private String serialize(Object snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(snapshot.getClass().getSimpleName() + " cannot be serialized", exception);
        }
    }
}
