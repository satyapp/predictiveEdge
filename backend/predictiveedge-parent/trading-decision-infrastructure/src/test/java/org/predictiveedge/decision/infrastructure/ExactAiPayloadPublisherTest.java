package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.application.AiResourcePayloadPublicationPort;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.ShadowScope;

class ExactAiPayloadPublisherTest {
    private static final Instant AVAILABLE_AT = Instant.parse("2026-08-15T05:00:00Z");
    private static final ShadowScope SCOPE = new ShadowScope(UUID.randomUUID(),
            new InstrumentRef("NSE", "INE002A01018"));
    private static final String HASH = "a".repeat(64);

    @Test
    void publishesTheExactJsonAgainstTheSelectedResourceIdentity() {
        AiResourcePayloadPublicationPort port = mock(AiResourcePayloadPublicationPort.class);
        when(port.append(eq(SCOPE), eq(DecisionResourceType.RISK), eq("risk-snapshot:risk-1"),
                eq(HASH), eq(AVAILABLE_AT), contains("remainingRisk"))).thenReturn(true);
        var publisher = new ExactAiPayloadPublisher(new ObjectMapper().findAndRegisterModules(), port);

        publisher.publish(SCOPE, DecisionResourceType.RISK, resource(), Map.of("remainingRisk", 1250));

        verify(port).append(eq(SCOPE), eq(DecisionResourceType.RISK), eq("risk-snapshot:risk-1"),
                eq(HASH), eq(AVAILABLE_AT), contains("\"remainingRisk\":1250"));
    }

    @Test
    void failsClosedWhenTheStoreDetectsAConflictingPayload() {
        AiResourcePayloadPublicationPort port = mock(AiResourcePayloadPublicationPort.class);
        when(port.append(eq(SCOPE), eq(DecisionResourceType.RISK), eq("risk-snapshot:risk-1"),
                eq(HASH), eq(AVAILABLE_AT), contains("remainingRisk"))).thenReturn(false);
        var publisher = new ExactAiPayloadPublisher(new ObjectMapper().findAndRegisterModules(), port);

        assertThatThrownBy(() -> publisher.publish(
                SCOPE, DecisionResourceType.RISK, resource(), Map.of("remainingRisk", 1250)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting AI payload");
    }

    private static DecisionResource resource() {
        return new DecisionResource("risk:risk-1", DecisionResourceType.RISK, SCOPE.userId(), SCOPE.instrument(),
                AssessmentReadiness.READY, GateDisposition.PASS, AVAILABLE_AT.minusSeconds(3),
                AVAILABLE_AT.minusSeconds(2), AVAILABLE_AT, AVAILABLE_AT.plusSeconds(30),
                "risk-snapshot:risk-1", HASH);
    }
}
