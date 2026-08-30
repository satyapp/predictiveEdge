package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.predictiveedge.decision.application.ShadowDecisionInputQuery;
import org.predictiveedge.decision.application.ShadowEvidenceBatchStore;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.ShadowEvidenceBatch;
import org.predictiveedge.decision.domain.ShadowScope;
import org.predictiveedge.decision.domain.TraderIntent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Causal evidence batch store and point-in-time bundle query for the fixed shadow scope. */
public final class JdbcShadowEvidenceStore implements ShadowEvidenceBatchStore, ShadowDecisionInputQuery {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Supplier<String> bundleIds;

    public JdbcShadowEvidenceStore(JdbcTemplate jdbc, ObjectMapper json, Supplier<String> bundleIds) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.bundleIds = Objects.requireNonNull(bundleIds, "Bundle id supplier is required");
    }

    @Override
    @Transactional
    public boolean append(ShadowEvidenceBatch batch) {
        Objects.requireNonNull(batch, "Shadow evidence batch is required");
        var scope = batch.scope();
        jdbc.update("""
                insert into decision.shadow_scope (
                  singleton_key,user_id,venue,instrument_id,mode,configured_at)
                values (1,?,?,?,'SHADOW',?)
                on conflict (singleton_key) do nothing
                """, scope.userId(), scope.instrument().venue(), scope.instrument().instrumentId(),
                Timestamp.from(batch.capturedAt()));
        int changed = jdbc.update("""
                insert into decision.shadow_evidence_batch (
                  batch_id,user_id,venue,instrument_id,captured_at,valid_until,
                  analysis_cutoff,knowledge_cutoff,manifest_hash,batch_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, batch.batchId(), scope.userId(), scope.instrument().venue(), scope.instrument().instrumentId(),
                Timestamp.from(batch.capturedAt()), Timestamp.from(batch.validUntil()),
                Timestamp.from(batch.evidenceManifest().analysisCutoff()),
                Timestamp.from(batch.evidenceManifest().knowledgeCutoff()),
                batch.evidenceManifest().manifestHash(), serialize(batch));
        return changed == 1;
    }

    @Override
    public AITradingDecisionInputBundle assemble(ShadowScope scope, TraderIntent traderIntent, Instant cutoff) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(traderIntent, "Trader intent is required");
        Objects.requireNonNull(cutoff, "Cutoff is required");
        scope.requireMatches(traderIntent.traderId(), traderIntent.instrument());
        ShadowEvidenceBatch batch = jdbc.query("""
                select batch_json::text
                from decision.shadow_evidence_batch
                where user_id=? and venue=? and instrument_id=?
                  and captured_at<=? and knowledge_cutoff<=? and valid_until>?
                order by captured_at desc
                limit 1
                """, this::mapBatch, scope.userId(), scope.instrument().venue(), scope.instrument().instrumentId(),
                Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No valid point-in-time shadow evidence batch exists"));
        return batch.toBundle(required(bundleIds.get(), "Bundle id"), traderIntent.intentId(), cutoff);
    }

    private ShadowEvidenceBatch mapBatch(ResultSet result, int rowNumber) throws SQLException {
        try {
            return json.readValue(result.getString("batch_json"), ShadowEvidenceBatch.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored shadow evidence batch is invalid", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Shadow evidence batch cannot be serialized", exception);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
