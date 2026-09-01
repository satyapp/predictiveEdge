package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.predictiveedge.decision.application.AiResourcePayloadPublicationPort;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ShadowScope;

/** Serializes and append-publishes the exact snapshot selected by a decision resource. */
public final class ExactAiPayloadPublisher {
    private final ObjectMapper json;
    private final AiResourcePayloadPublicationPort payloads;

    public ExactAiPayloadPublisher(ObjectMapper json, AiResourcePayloadPublicationPort payloads) {
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.payloads = Objects.requireNonNull(payloads, "AI payload publication port is required");
    }

    public DecisionResource publish(ShadowScope scope, DecisionResourceType expectedType,
            DecisionResource resource, Object exactSnapshot) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(expectedType, "Expected resource type is required");
        Objects.requireNonNull(resource, "Decision resource is required");
        Objects.requireNonNull(exactSnapshot, "Exact snapshot is required");
        scope.requireMatches(resource.userId(), resource.instrument());
        if (resource.type() != expectedType) throw new IllegalArgumentException("Decision resource type mismatch");
        boolean accepted = payloads.append(scope, expectedType, resource.payloadRef(), resource.evidenceHash(),
                resource.availableAt(), serialize(exactSnapshot));
        if (!accepted) throw new IllegalStateException("Conflicting AI payload exists for "
                + expectedType + " reference " + resource.payloadRef());
        return resource;
    }

    private String serialize(Object snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(snapshot.getClass().getSimpleName() + " cannot be serialized", exception);
        }
    }
}
