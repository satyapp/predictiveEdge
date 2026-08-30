package org.predictiveedge.marketintelligence.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.application.MarketContextPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketContextQueryPort;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketContextKey;
import org.predictiveedge.marketintelligence.domain.MarketContextSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;

/** Append-only PostgreSQL store and causal reader for semantic Market Context snapshots. */
public final class JdbcMarketContextStore implements MarketContextPublicationPort, MarketContextQueryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcMarketContextStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    public boolean append(UUID userId, MarketContextSnapshot snapshot) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(snapshot, "Market Context snapshot is required");
        int changed = jdbc.update("""
                insert into market_intelligence.market_context_snapshot (
                  user_id,scope_type,scope_id,horizon,analysis_cutoff,knowledge_cutoff,
                  decision_ready_at,expires_at,semantic_hash,snapshot_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, userId, snapshot.key().scopeType().name(), snapshot.key().scopeId(), snapshot.key().horizon(),
                Timestamp.from(snapshot.cutoff().analysisCutoff()), Timestamp.from(snapshot.cutoff().knowledgeCutoff()),
                Timestamp.from(snapshot.decisionReadyAt()), Timestamp.from(snapshot.expiresAt()),
                snapshot.semanticHash().value(), serialize(snapshot));
        return changed == 1;
    }

    @Override
    public Optional<MarketContextSnapshot> findLatest(
            UUID userId, MarketContextKey key, EvaluationCutoff cutoff) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(key, "Market Context key is required");
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        return jdbc.query("""
                select snapshot_json::text
                from market_intelligence.market_context_snapshot
                where user_id=? and scope_type=? and scope_id=? and horizon=?
                  and analysis_cutoff<=? and knowledge_cutoff<=? and decision_ready_at<=?
                order by analysis_cutoff desc,knowledge_cutoff desc,decision_ready_at desc
                limit 1
                """, this::mapSnapshot, userId, key.scopeType().name(), key.scopeId(), key.horizon(),
                Timestamp.from(cutoff.analysisCutoff()), Timestamp.from(cutoff.knowledgeCutoff()),
                Timestamp.from(cutoff.knowledgeCutoff())).stream().findFirst();
    }

    private MarketContextSnapshot mapSnapshot(ResultSet result, int rowNumber) throws SQLException {
        try {
            return json.readValue(result.getString("snapshot_json"), MarketContextSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Market Context snapshot is invalid", exception);
        }
    }

    private String serialize(MarketContextSnapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Market Context snapshot cannot be serialized", exception);
        }
    }
}
