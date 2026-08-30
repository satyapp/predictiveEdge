package org.predictiveedge.decision.application;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.DecisionResourceType;

/** Complete provider-neutral AI input: governed envelope plus every resolved immutable payload. */
public record AiModelEvidenceInput(
        AITradingDecisionInputBundle bundle,
        Map<DecisionResourceType, String> resourcePayloadJson) {

    public AiModelEvidenceInput {
        Objects.requireNonNull(bundle, "AI input bundle is required");
        Objects.requireNonNull(resourcePayloadJson, "Resolved resource payloads are required");
        EnumMap<DecisionResourceType, String> copy = new EnumMap<>(DecisionResourceType.class);
        resourcePayloadJson.forEach((type, payload) -> {
            Objects.requireNonNull(type, "Resource payload type cannot be null");
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Resolved payload is required for " + type);
            }
            copy.put(type, payload.trim());
        });
        if (copy.size() != DecisionResourceType.values().length) {
            throw new IllegalArgumentException("All twelve resolved resource payloads are required");
        }
        resourcePayloadJson = Map.copyOf(copy);
    }
}
