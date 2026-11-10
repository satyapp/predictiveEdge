package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.IntradaySetupAction;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;

class PreOpenIntradayAnalysisServiceTest {
    @Test
    void requestsTenantOwnedFiveMinuteHistoryWithPointInTimeCutoffs() {
        var port = new RecordingPort();
        var service = new PreOpenIntradayAnalysisService(port, noStoredSnapshots(),
                new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults()));
        UUID userId = UUID.randomUUID();
        Instant analysisCutoff = Instant.parse("2026-11-09T03:40:00Z");
        Instant knowledgeCutoff = analysisCutoff.plusSeconds(5);
        var snapshot = new PreOpenSnapshot("NSE", "SBIN", "EQ", bd("1016"), bd("1030"),
                573_581, bd("590788430"), bd("1030"), bd("1030.40"), analysisCutoff.minusSeconds(30));

        var result = service.analyze(new PreOpenIntradayAnalysisService.Command(userId, "ZD123", snapshot,
                analysisCutoff, knowledgeCutoff, Duration.ofDays(90)));

        assertThat(result.action()).isEqualTo(IntradaySetupAction.INSUFFICIENT_EVIDENCE);
        assertThat(port.criteria.userId()).isEqualTo(userId);
        assertThat(port.criteria.brokerAccountId()).isEqualTo("ZD123");
        assertThat(port.criteria.subject().id()).isEqualTo("NSE:SBIN");
        assertThat(port.criteria.timeframe()).isEqualTo(BarTimeframe.FIVE_MINUTES);
        assertThat(port.criteria.fromInclusive()).isEqualTo(analysisCutoff.minus(Duration.ofDays(90)));
        assertThat(port.criteria.cutoff()).isEqualTo(new EvaluationCutoff(analysisCutoff, knowledgeCutoff));
        assertThat(port.maximumResults).isEqualTo(15_000);
    }

    @Test
    void loadsStoredSnapshotWithBothCausalCutoffsBeforeReadingHistory() {
        var bars = new RecordingPort();
        UUID userId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-11-09T03:39:30Z");
        Instant receivedAt = observedAt.plusSeconds(2);
        var observation = new PreOpenAuctionObservation("NSE", "SBIN", "EQ", LocalDate.of(2026, 11, 9),
                bd("1016"), bd("1030"), 573_581, bd("590788430"), bd("1030"), bd("1030.40"), observedAt);
        var stored = new PreOpenAuctionSnapshot(UUID.randomUUID(), userId, "ZD123", observation, receivedAt,
                "a".repeat(64));
        var snapshots = new RecordingSnapshotPort(stored);
        var service = new PreOpenIntradayAnalysisService(bars, snapshots,
                new PreOpenIntradayAnalyzer(PreOpenIntradayAnalyzer.Policy.conservativeDefaults()));
        Instant analysisCutoff = observedAt.plusSeconds(30);
        Instant knowledgeCutoff = analysisCutoff.plusSeconds(5);

        var result = service.analyzeStored(new PreOpenIntradayAnalysisService.StoredCommand(userId, "ZD123",
                "nse", "sbin", observation.tradingDate(), analysisCutoff, knowledgeCutoff, Duration.ofDays(90)));

        assertThat(result).isPresent();
        assertThat(snapshots.analysisCutoff).isEqualTo(analysisCutoff);
        assertThat(snapshots.knowledgeCutoff).isEqualTo(knowledgeCutoff);
        assertThat(snapshots.venue).isEqualTo("NSE");
        assertThat(snapshots.symbol).isEqualTo("SBIN");
        assertThat(bars.criteria.subject().id()).isEqualTo("NSE:SBIN");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static PreOpenAuctionQueryPort noStoredSnapshots() {
        return (userId, accountId, venue, symbol, tradingDate, analysisCutoff, knowledgeCutoff) -> Optional.empty();
    }

    private static final class RecordingSnapshotPort implements PreOpenAuctionQueryPort {
        private final PreOpenAuctionSnapshot stored;
        private String venue;
        private String symbol;
        private Instant analysisCutoff;
        private Instant knowledgeCutoff;

        private RecordingSnapshotPort(PreOpenAuctionSnapshot stored) { this.stored = stored; }

        @Override
        public Optional<PreOpenAuctionSnapshot> latestAtOrBefore(UUID userId, String brokerAccountId,
                String venue, String symbol, LocalDate tradingDate, Instant analysisCutoff, Instant knowledgeCutoff) {
            this.venue = venue;
            this.symbol = symbol;
            this.analysisCutoff = analysisCutoff;
            this.knowledgeCutoff = knowledgeCutoff;
            return Optional.of(stored);
        }
    }

    private static final class RecordingPort implements MarketBarQueryPort {
        private MarketBarReplayCriteria criteria;
        private int maximumResults;

        @Override
        public Optional<MarketBarRevision> findLatest(UUID userId, String brokerAccountId,
                ObservationSubject subject, BarTimeframe timeframe, EvaluationCutoff cutoff) {
            return Optional.empty();
        }

        @Override
        public List<MarketBarRevision> replay(MarketBarReplayCriteria criteria, int maximumResults) {
            this.criteria = criteria;
            this.maximumResults = maximumResults;
            return List.of();
        }
    }
}
