package org.predictiveedge.platform.eventing.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class EventEnvelopeTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-31T04:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void computesDeterministicHashIndependentOfObjectFieldOrder() {
        ObjectNode first = JSON.createObjectNode().put("symbol", "INFY").put("price", 1500);
        ObjectNode reordered = JSON.createObjectNode().put("price", 1500).put("symbol", "INFY");

        assertThat(PayloadHasher.sha256(first)).isEqualTo(PayloadHasher.sha256(reordered));
    }

    @Test
    void rejectsPayloadTampering() {
        ObjectNode original = JSON.createObjectNode().put("symbol", "INFY").put("price", 1500);
        EventEnvelope envelope = EventEnvelope.create(metadata(OCCURRED_AT, OCCURRED_AT), original,
                DataClassification.INTERNAL);
        ObjectNode altered = original.deepCopy().put("price", 1600);

        assertThatThrownBy(() -> new EventEnvelope(
                envelope.metadata(), altered, envelope.payloadHash(), envelope.classification()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void protectsPayloadAndContextReferencesFromExternalMutation() {
        ObjectNode payload = JSON.createObjectNode().put("symbol", "INFY");
        EventEnvelope envelope = EventEnvelope.create(metadata(OCCURRED_AT, OCCURRED_AT), payload,
                DataClassification.INTERNAL);

        payload.put("symbol", "TCS");
        ((ObjectNode) envelope.payload()).put("symbol", "WIPRO");

        assertThat(envelope.payload().get("symbol").textValue()).isEqualTo("INFY");
        assertThat(envelope.metadata().contextReferences()).hasSize(1);
        assertThatThrownBy(() -> envelope.metadata().contextReferences().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void roundTripsThroughJsonWithoutChangingIdentityOrHash() throws Exception {
        EventEnvelope envelope = EventEnvelope.create(
                metadata(OCCURRED_AT, OCCURRED_AT),
                JSON.createObjectNode().put("symbol", "INFY").put("close", "1500.25"),
                DataClassification.CONFIDENTIAL);

        EventEnvelope restored = JSON.readValue(JSON.writeValueAsBytes(envelope), EventEnvelope.class);

        assertThat(restored).isEqualTo(envelope);
        assertThat(restored.payloadHash()).isEqualTo(PayloadHasher.sha256(restored.payload()));
    }

    @Test
    void acceptsInformationKnownAtBothCutoffBoundaries() {
        EventEnvelope envelope = EventEnvelope.create(
                metadata(OCCURRED_AT, OCCURRED_AT), JSON.createObjectNode().put("state", "known"),
                DataClassification.INTERNAL);

        assertThat(envelope.isPointInTimeEligible()).isTrue();
    }

    @Test
    void rejectsInformationEffectiveAfterAnalysisCutoff() {
        EventEnvelope envelope = EventEnvelope.create(
                metadata(OCCURRED_AT.plusSeconds(1), OCCURRED_AT),
                JSON.createObjectNode().put("state", "future"), DataClassification.INTERNAL);

        assertThat(envelope.isPointInTimeEligible()).isFalse();
    }

    @Test
    void rejectsInformationUnavailableAtKnowledgeCutoff() {
        EventEnvelope envelope = EventEnvelope.create(
                metadata(OCCURRED_AT, OCCURRED_AT.plusSeconds(1)),
                JSON.createObjectNode().put("state", "unknown"), DataClassification.INTERNAL);

        assertThat(envelope.isPointInTimeEligible()).isFalse();
    }

    @Test
    void requiresPositiveAggregateAndContextVersions() {
        assertThatThrownBy(() -> new SchemaVersion(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextReference("market-context", "nse", 0, "abc123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EventMetadata metadata(Instant effectiveAt, Instant availableAt) {
        return new EventMetadata(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "MarketContext.Changed",
                new SchemaVersion(1, 0),
                "market-intelligence",
                "MarketContext",
                "NSE:INTRADAY",
                1,
                "NSE:INTRADAY",
                OCCURRED_AT,
                effectiveAt,
                availableAt,
                null,
                OCCURRED_AT,
                OCCURRED_AT,
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                null,
                null,
                null,
                null,
                List.of(new ContextReference("MarketContext", "NSE:INTRADAY", 1, "abc123")),
                null);
    }
}
