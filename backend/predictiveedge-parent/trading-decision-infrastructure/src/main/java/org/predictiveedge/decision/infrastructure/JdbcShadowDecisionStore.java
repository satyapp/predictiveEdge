package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Objects;
import org.predictiveedge.decision.application.ShadowDecisionCaseStore;
import org.predictiveedge.decision.application.ShadowOutcomeStore;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.RecommendationOutcomeContract;
import org.predictiveedge.decision.domain.ResolvedModelOutcome;
import org.predictiveedge.decision.domain.ShadowDecisionCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL append-only store for the one-user/one-equity shadow MVP. */
public final class JdbcShadowDecisionStore implements ShadowDecisionCaseStore, ShadowOutcomeStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcShadowDecisionStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    @Transactional
    public boolean append(ShadowDecisionCase decisionCase) {
        Objects.requireNonNull(decisionCase, "Shadow decision case is required");
        var scope = decisionCase.inputBundle().scope();
        jdbc.update("""
                insert into decision.shadow_scope (
                  singleton_key,user_id,venue,instrument_id,mode,configured_at)
                values (1,?,?,?,'SHADOW',?)
                on conflict (singleton_key) do nothing
                """, scope.userId(), scope.instrument().venue(), scope.instrument().instrumentId(),
                Timestamp.from(decisionCase.recordedAt()));
        AIRecommendation recommendation = decisionCase.recommendation();
        int changed = jdbc.update("""
                insert into decision.shadow_decision_case (
                  case_id,bundle_id,recommendation_id,user_id,venue,instrument_id,status,action,
                  evaluated_at,manifest_hash,input_bundle_json,recommendation_json,policy_reasons_json,recorded_at)
                values (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)
                on conflict do nothing
                """, decisionCase.caseId(), decisionCase.inputBundle().bundleId(),
                recommendation == null ? null : recommendation.recommendationId(), scope.userId(),
                scope.instrument().venue(), scope.instrument().instrumentId(), decisionCase.status().name(),
                recommendation == null ? null : recommendation.action().name(),
                Timestamp.from(decisionCase.inputBundle().assembledAt()),
                decisionCase.inputBundle().evidenceManifest().manifestHash(), serialize(decisionCase.inputBundle()),
                recommendation == null ? null : serialize(recommendation), serialize(decisionCase.policyReasons()),
                Timestamp.from(decisionCase.recordedAt()));
        return changed == 1;
    }

    @Override
    @Transactional
    public boolean append(RecommendationOutcomeContract contract, ResolvedModelOutcome outcome) {
        Objects.requireNonNull(contract, "Outcome contract is required");
        Objects.requireNonNull(outcome, "Resolved model outcome is required");
        if (!contract.recommendationId().equals(outcome.recommendationId())) {
            throw new IllegalArgumentException("Outcome must reference its frozen contract");
        }
        int changed = jdbc.update("""
                insert into decision.shadow_model_outcome (
                  recommendation_id,outcome,net_return_after_costs,resolved_at,
                  contract_json,outcome_json,outcome_definition_version)
                values (?,?,?,?,?::jsonb,?::jsonb,?)
                on conflict do nothing
                """, outcome.recommendationId(), outcome.outcome().name(), outcome.netReturnAfterCosts(),
                Timestamp.from(outcome.resolvedAt()), serialize(contract), serialize(outcome),
                outcome.outcomeDefinitionVersion());
        return changed == 1;
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Shadow decision evidence cannot be serialized", exception);
        }
    }
}
