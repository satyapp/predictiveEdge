package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketSessionTest {

    @Test
    void alignsFiveMinuteBarsToTheNseContinuousSessionAnchor() {
        var session = regularNseSession();

        assertThat(session.barIntervalAt(at("2026-08-03T03:47:00Z"), BarTimeframe.FIVE_MINUTES))
                .contains(new BarInterval(at("2026-08-03T03:45:00Z"), at("2026-08-03T03:50:00Z"), false));
        assertThat(session.barIntervalAt(at("2026-08-03T03:50:00Z"), BarTimeframe.FIVE_MINUTES))
                .contains(new BarInterval(at("2026-08-03T03:50:00Z"), at("2026-08-03T03:55:00Z"), false));
    }

    @Test
    void doesNotAggregatePreOpenOrHaltedFacts() {
        var session = regularNseSession();

        assertThat(session.barIntervalAt(at("2026-08-03T03:40:00Z"), BarTimeframe.ONE_MINUTE)).isEmpty();
        assertThat(session.barIntervalAt(at("2026-08-03T05:05:00Z"), BarTimeframe.ONE_MINUTE)).isEmpty();
    }

    @Test
    void aHaltDoesNotResetTheCanonicalBarGrid() {
        var session = regularNseSession();

        assertThat(session.barIntervalAt(at("2026-08-03T05:17:00Z"), BarTimeframe.FIFTEEN_MINUTES))
                .contains(new BarInterval(at("2026-08-03T05:15:00Z"), at("2026-08-03T05:30:00Z"), false));
    }

    @Test
    void specialSessionUsesItsOwnCalendarSuppliedAnchor() {
        var special = new MarketSession(
                new MarketSessionId("NSE", LocalDate.of(2026, 11, 8), "MUHURAT"),
                at("2026-11-08T12:45:00Z"), at("2026-11-08T13:45:00Z"),
                List.of(new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS,
                        at("2026-11-08T12:45:00Z"), at("2026-11-08T13:45:00Z"))),
                "NSE-2026-v3");

        assertThat(special.barIntervalAt(at("2026-11-08T12:51:00Z"), BarTimeframe.FIVE_MINUTES))
                .contains(new BarInterval(at("2026-11-08T12:50:00Z"), at("2026-11-08T12:55:00Z"), false));
    }

    @Test
    void truncatesTheLastFixedDurationBarAtSessionEnd() {
        var interval = regularNseSession()
                .barIntervalAt(at("2026-08-03T09:50:00Z"), BarTimeframe.ONE_HOUR)
                .orElseThrow();

        assertThat(interval).isEqualTo(
                new BarInterval(at("2026-08-03T09:45:00Z"), at("2026-08-03T10:00:00Z"), true));
    }

    @Test
    void rejectsOverlappingCalendarPhases() {
        assertThatThrownBy(() -> new MarketSession(
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 3), "REGULAR"),
                at("2026-08-03T03:45:00Z"), at("2026-08-03T10:00:00Z"),
                List.of(
                        new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS,
                                at("2026-08-03T03:45:00Z"), at("2026-08-03T05:15:00Z")),
                        new SessionPhaseWindow(MarketSessionPhase.HALTED,
                                at("2026-08-03T05:00:00Z"), at("2026-08-03T05:30:00Z"))),
                "NSE-2026-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not overlap");
    }

    private static MarketSession regularNseSession() {
        return new MarketSession(
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 3), "REGULAR"),
                at("2026-08-03T03:45:00Z"), at("2026-08-03T10:00:00Z"),
                List.of(
                        new SessionPhaseWindow(MarketSessionPhase.PRE_OPEN,
                                at("2026-08-03T03:30:00Z"), at("2026-08-03T03:45:00Z")),
                        new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS,
                                at("2026-08-03T03:45:00Z"), at("2026-08-03T05:00:00Z")),
                        new SessionPhaseWindow(MarketSessionPhase.HALTED,
                                at("2026-08-03T05:00:00Z"), at("2026-08-03T05:15:00Z")),
                        new SessionPhaseWindow(MarketSessionPhase.CONTINUOUS,
                                at("2026-08-03T05:15:00Z"), at("2026-08-03T10:00:00Z"))),
                "NSE-2026-v2");
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
