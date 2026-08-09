package org.predictiveedge.marketintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.SessionPhaseWindow;

class MarketSessionCalendarServiceTest {
    private static final Instant COVERAGE_START = Instant.parse("2026-08-07T03:30:00Z");
    private static final Instant OPEN = Instant.parse("2026-08-07T03:45:00Z");
    private static final Instant CLOSE = Instant.parse("2026-08-07T10:00:00Z");
    private static final Instant COVERAGE_END = Instant.parse("2026-08-07T10:15:00Z");

    @Test
    void delegatesAValidatedDefinitionToThePublicationBoundary() {
        var definition = definition();
        var service = new MarketSessionCalendarService(candidate ->
                new MarketSessionPublicationResult(candidate.definitionId(),
                        MarketSessionPublicationResult.Status.CREATED));

        assertThat(service.publish(definition).status())
                .isEqualTo(MarketSessionPublicationResult.Status.CREATED);
    }

    @Test
    void rejectsCoverageThatDoesNotContainTheSession() {
        assertThatThrownBy(() -> new MarketSessionDefinition(UUID.randomUUID(), session(), OPEN.plusSeconds(1),
                COVERAGE_END, COVERAGE_START, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete bar-producing session");
    }

    @Test
    void rejectsInvalidEffectiveRange() {
        assertThatThrownBy(() -> new MarketSessionDefinition(UUID.randomUUID(), session(), COVERAGE_START,
                COVERAGE_END, COVERAGE_END, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before coverage ends");

        assertThatThrownBy(() -> new MarketSessionDefinition(UUID.randomUUID(), session(), COVERAGE_START,
                COVERAGE_END, COVERAGE_START.minusSeconds(7200), COVERAGE_START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap the coverage interval");
    }

    private static MarketSessionDefinition definition() {
        return new MarketSessionDefinition(UUID.randomUUID(), session(), COVERAGE_START, COVERAGE_END,
                COVERAGE_START.minusSeconds(3600), null);
    }

    private static MarketSession session() {
        return new MarketSession(new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"),
                OPEN, CLOSE, List.of(
                new SessionPhaseWindow(MarketSessionPhase.PRE_OPEN, COVERAGE_START, OPEN),
                new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS, OPEN, CLOSE),
                new SessionPhaseWindow(MarketSessionPhase.CLOSED, CLOSE, COVERAGE_END)), "nse-v1");
    }
}
