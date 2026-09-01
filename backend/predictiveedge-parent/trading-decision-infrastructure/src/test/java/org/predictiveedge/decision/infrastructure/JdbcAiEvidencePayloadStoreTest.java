package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAiEvidencePayloadStoreTest {
    @Test
    void acceptsAnInsertOrAnIdenticalIdempotentConflictOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var store = new JdbcAiEvidencePayloadStore(jdbc);
        var bundle = OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY);

        assertThat(store.append(bundle.scope(), DecisionResourceType.RISK, "risk-snapshot:risk-1",
                "a".repeat(64), bundle.assembledAt(), "{\"risk\":true}")).isTrue();
    }

    @Test
    void reportsAConflictingPayloadAsNotAccepted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        var store = new JdbcAiEvidencePayloadStore(jdbc);
        var bundle = OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY);

        assertThat(store.append(bundle.scope(), DecisionResourceType.RISK, "risk-snapshot:risk-1",
                "a".repeat(64), bundle.assembledAt(), "{\"risk\":false}")).isFalse();
    }

    @Test
    void resolvesEveryExactPayloadWithoutDroppingAnyResource() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("{\"complete\":true}"));
        var store = new JdbcAiEvidencePayloadStore(jdbc);

        var resolved = store.resolve(OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY));

        assertThat(resolved.resourcePayloadJson()).hasSize(DecisionResourceType.values().length)
                .allSatisfy((type, payload) -> assertThat(payload).contains("complete"));
    }

    @Test
    void failsClosedWhenEvenOneExactPayloadIsMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of());
        var store = new JdbcAiEvidencePayloadStore(jdbc);

        assertThatThrownBy(() -> store.resolve(OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Exact AI payload is unavailable");
    }
}
