package org.predictiveedge.decision.infrastructure;

import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import org.predictiveedge.decision.application.AiEvidencePayloadQuery;
import org.predictiveedge.decision.application.AiModelEvidenceInput;
import org.predictiveedge.decision.application.AiResourcePayloadPublicationPort;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ShadowScope;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact-reference payload resolver. It fails closed if any of the twelve payloads is absent or mismatched. */
public final class JdbcAiEvidencePayloadStore implements AiResourcePayloadPublicationPort, AiEvidencePayloadQuery {
    private final JdbcTemplate jdbc;

    public JdbcAiEvidencePayloadStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    public boolean append(ShadowScope scope, DecisionResourceType type, String payloadRef, String evidenceHash,
            java.time.Instant availableAt, String payloadJson) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(type, "Resource type is required");
        return jdbc.update("""
                insert into decision.ai_resource_payload (
                  user_id,venue,instrument_id,resource_type,payload_ref,evidence_hash,available_at,payload_json)
                values (?,?,?,?,?,?,?,?::jsonb)
                on conflict (user_id,venue,instrument_id,resource_type,payload_ref,evidence_hash)
                do update set payload_json=excluded.payload_json
                where decision.ai_resource_payload.payload_json=excluded.payload_json
                  and decision.ai_resource_payload.available_at=excluded.available_at
                """, scope.userId(), scope.instrument().venue(), scope.instrument().instrumentId(), type.name(),
                required(payloadRef, "Payload reference"), hash(evidenceHash), Timestamp.from(availableAt),
                required(payloadJson, "Payload JSON")) == 1;
    }

    @Override
    public AiModelEvidenceInput resolve(AITradingDecisionInputBundle bundle) {
        Objects.requireNonNull(bundle, "AI input bundle is required");
        EnumMap<DecisionResourceType, String> payloads = new EnumMap<>(DecisionResourceType.class);
        for (DecisionResourceType type : DecisionResourceType.values()) {
            var resource = bundle.resources().get(type);
            List<String> matches = jdbc.queryForList("""
                    select payload_json::text
                    from decision.ai_resource_payload
                    where user_id=? and venue=? and instrument_id=? and resource_type=?
                      and payload_ref=? and evidence_hash=? and available_at<=?
                    limit 1
                    """, String.class, bundle.userId(), bundle.instrument().venue(), bundle.instrument().instrumentId(),
                    type.name(), resource.payloadRef(), resource.evidenceHash(), Timestamp.from(bundle.assembledAt()));
            String payload = matches.stream().findFirst().orElseThrow(() -> new IllegalStateException(
                    "Exact AI payload is unavailable for " + type + " reference " + resource.payloadRef()));
            payloads.put(type, payload);
        }
        return new AiModelEvidenceInput(bundle, payloads);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String hash(String value) {
        String normalized = required(value, "Evidence hash").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence hash must be SHA-256");
        return normalized;
    }
}
