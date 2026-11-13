package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.chart.domain.ChartTrigger;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;
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

class PreOpenCandidateConfirmationServiceTest {
    @Test
    void publishesFinalChartEvidenceAfterOpeningRangeAndVwapConfirmation() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 9);
        Instant open = Instant.parse("2026-11-09T03:45:00Z");
        Instant cutoff = open.plus(Duration.ofMinutes(5));
        var observation = new PreOpenAuctionObservation("NSE", "SBIN", "EQ", date,
                bd("100"), bd("101"), 100_000, bd("10100000"), bd("100.90"), bd("101.00"),
                Instant.parse("2026-11-09T03:39:30Z"));
        var stored = new PreOpenAuctionSnapshot(UUID.randomUUID(), userId, "ZD123", observation,
                observation.observedAt().plusSeconds(2), "a".repeat(64));
        var bars = new FakeBars(open, historicalBars(open), sessionBars(open, date));
        var preOpen = new PreOpenIntradayAnalysisService(bars,
                (user, account, venue, symbol, tradingDate, analysis, knowledge) -> Optional.of(stored),
                new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults()));
        AtomicReference<ChartSnapshot> published = new AtomicReference<>();
        var service = new PreOpenCandidateConfirmationService(preOpen, bars,
                (user, snapshot) -> published.compareAndSet(null, snapshot),
                Clock.fixed(cutoff.plusSeconds(6), ZoneOffset.UTC));

        var result = service.confirm(new PreOpenCandidateConfirmationService.Command(userId, "ZD123", "NSE",
                "SBIN", date, cutoff, cutoff.plusSeconds(5), Duration.ofDays(365)));

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.chartSnapshot().trigger()).isEqualTo(ChartTrigger.DOWNSIDE_BREAKDOWN);
        assertThat(result.appended()).isTrue();
        assertThat(published.get()).isEqualTo(result.chartSnapshot());
    }

    private static List<MarketBarRevision> historicalBars(Instant open) {
        var values = new ArrayList<MarketBarRevision>();
        Instant start = open.minus(Duration.ofMinutes(5L * 60));
        for (int index = 0; index < 60; index++) {
            values.add(bar(start.plus(Duration.ofMinutes(5L * index)), LocalDate.of(2026, 11, 6),
                    bd("100"), bd("100.20"), bd("99.80"), bd("100"), 1_000));
        }
        return List.copyOf(values);
    }

    private static List<MarketBarRevision> sessionBars(Instant open, LocalDate date) {
        return List.of(bar(open, date, bd("100.5"), bd("101"), bd("97.8"), bd("98"), 20_000));
    }

    private static MarketBarRevision bar(Instant start, LocalDate date, BigDecimal open, BigDecimal high,
            BigDecimal low, BigDecimal close, long volume) {
        var key = new MarketBarKey(new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:SBIN"),
                new MarketSessionId("NSE", date, "REGULAR"), BarTimeframe.FIVE_MINUTES,
                new BarInterval(start, start.plusSeconds(300), false));
        return new MarketBarRevision(key, 1, new MarketBarValues(open, high, low, close, volume),
                start.plusSeconds(300), BarFinalityState.FINAL, start.plusSeconds(300), null,
                new ContentHash("b".repeat(64)), "historical-v1", "final-v1");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static final class FakeBars implements MarketBarQueryPort {
        private final Instant sessionOpen;
        private final List<MarketBarRevision> historical;
        private final List<MarketBarRevision> session;

        private FakeBars(Instant sessionOpen, List<MarketBarRevision> historical, List<MarketBarRevision> session) {
            this.sessionOpen = sessionOpen; this.historical = historical; this.session = session;
        }

        @Override
        public Optional<MarketBarRevision> findLatest(UUID userId, String brokerAccountId,
                ObservationSubject subject, BarTimeframe timeframe, EvaluationCutoff cutoff) {
            return Optional.empty();
        }

        @Override
        public List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults) {
            return criteria.fromInclusive().equals(sessionOpen) ? session : historical;
        }
    }
}
