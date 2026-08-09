package org.predictiveedge.marketintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

class MarketBarQueryServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final ObservationSubject SUBJECT =
            new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:INFY");
    private static final Instant START = Instant.parse("2026-08-07T03:45:00Z");
    private static final EvaluationCutoff CUTOFF =
            new EvaluationCutoff(START.plusSeconds(600), START.plusSeconds(700));

    @Test
    void returnsABoundedPageAndAStableContinuationCursor() {
        var requestedLimits = new ArrayList<Integer>();
        MarketBarQueryPort port = new MarketBarQueryPort() {
            @Override
            public Optional<MarketBarRevision> findLatest(UUID userId, String accountId,
                    ObservationSubject subject, BarTimeframe timeframe, EvaluationCutoff cutoff) {
                return Optional.empty();
            }

            @Override
            public List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults) {
                requestedLimits.add(maximumResults);
                return List.of(bar(0), bar(1), bar(2));
            }
        };
        var service = new MarketBarQueryService(port);
        var criteria = new MarketBarReplayCriteria(USER_ID, "ZD123", SUBJECT, BarTimeframe.ONE_MINUTE,
                START, START.plusSeconds(600), CUTOFF, null);

        var page = service.replay(criteria, 2);

        assertThat(requestedLimits).containsExactly(3);
        assertThat(page.bars()).containsExactly(bar(0), bar(1));
        assertThat(page.next()).isEqualTo(new MarketBarReplayCursor(
                START.plusSeconds(60), "NSE", LocalDate.of(2026, 8, 7), "REGULAR"));
    }

    @Test
    void rejectsUnboundedPagesAndInvalidRanges() {
        MarketBarQueryPort unused = new MarketBarQueryPort() {
            public Optional<MarketBarRevision> findLatest(UUID userId, String accountId,
                    ObservationSubject subject, BarTimeframe timeframe, EvaluationCutoff cutoff) {
                return Optional.empty();
            }
            public List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults) {
                return List.of();
            }
        };
        var service = new MarketBarQueryService(unused);
        var criteria = new MarketBarReplayCriteria(USER_ID, "ZD123", SUBJECT, BarTimeframe.ONE_MINUTE,
                START, START.plusSeconds(60), CUTOFF, null);
        assertThatThrownBy(() -> service.replay(criteria, 1_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketBarReplayCriteria(USER_ID, "ZD123", SUBJECT,
                BarTimeframe.ONE_MINUTE, START, START, CUTOFF, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketBarRevision bar(int minute) {
        Instant startsAt = START.plusSeconds(60L * minute);
        var key = new MarketBarKey(SUBJECT,
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"), BarTimeframe.ONE_MINUTE,
                new BarInterval(startsAt, startsAt.plusSeconds(60), false));
        return new MarketBarRevision(key, 1,
                new MarketBarValues(new BigDecimal("100"), new BigDecimal("101"),
                        new BigDecimal("99"), new BigDecimal("100.5"), 10),
                startsAt.plusSeconds(50), BarFinalityState.FINAL, startsAt.plusSeconds(62), null,
                new ContentHash("a".repeat(64)), "tick-v1", "finality-v1");
    }
}
