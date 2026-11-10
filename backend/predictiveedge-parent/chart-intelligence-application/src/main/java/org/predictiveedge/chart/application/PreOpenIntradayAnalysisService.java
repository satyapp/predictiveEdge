package org.predictiveedge.chart.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.chart.domain.IntradayHistoricalBar;
import org.predictiveedge.chart.domain.IntradaySetupAssessment;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

/** Loads tenant-owned causal OHLCV history and evaluates one exchange pre-open snapshot. */
public final class PreOpenIntradayAnalysisService {
    private static final int MAXIMUM_HISTORY_BARS = 15_000;
    private final MarketBarQueryPort bars;
    private final PreOpenAuctionQueryPort preOpenSnapshots;
    private final PreOpenIntradayAnalyzer analyzer;

    public PreOpenIntradayAnalysisService(
            MarketBarQueryPort bars, PreOpenAuctionQueryPort preOpenSnapshots, PreOpenIntradayAnalyzer analyzer) {
        this.bars = Objects.requireNonNull(bars, "Market-bar query port is required");
        this.preOpenSnapshots = Objects.requireNonNull(preOpenSnapshots, "Pre-open query port is required");
        this.analyzer = Objects.requireNonNull(analyzer, "Pre-open analyzer is required");
    }

    public Optional<StoredAnalysis> analyzeStored(StoredCommand command) {
        Objects.requireNonNull(command, "Stored analysis command is required");
        return preOpenSnapshots.latestAtOrBefore(command.userId(), command.brokerAccountId(), command.venue(),
                command.symbol(), command.tradingDate(), command.analysisCutoff(), command.knowledgeCutoff())
                .map(stored -> {
                    var value = stored.observation();
                    var snapshot = new PreOpenSnapshot(value.venue(), value.symbol(), value.series(),
                            value.previousClose(), value.auctionPrice(), value.auctionVolume(),
                            value.auctionTurnover(), value.bestBid(), value.bestAsk(), value.observedAt());
                    var assessment = analyze(new Command(command.userId(), command.brokerAccountId(), snapshot,
                            command.analysisCutoff(), command.knowledgeCutoff(), command.historyWindow()));
                    return new StoredAnalysis(stored, assessment);
                });
    }

    public record StoredAnalysis(PreOpenAuctionSnapshot snapshot, IntradaySetupAssessment assessment) {
        public StoredAnalysis {
            Objects.requireNonNull(snapshot, "Stored snapshot is required");
            Objects.requireNonNull(assessment, "Assessment is required");
        }
    }

    public IntradaySetupAssessment analyze(Command command) {
        Objects.requireNonNull(command, "Analysis command is required");
        var subject = new ObservationSubject(ObservationSubjectType.INSTRUMENT,
                command.snapshot().venue() + ":" + command.snapshot().symbol());
        var cutoff = new EvaluationCutoff(command.analysisCutoff(), command.knowledgeCutoff());
        var criteria = new MarketBarReplayCriteria(command.userId(), command.brokerAccountId(), subject,
                BarTimeframe.FIVE_MINUTES, command.analysisCutoff().minus(command.historyWindow()),
                command.analysisCutoff(), cutoff, null);
        var history = bars.replay(criteria, MAXIMUM_HISTORY_BARS).stream()
                .map(value -> new IntradayHistoricalBar(value.key().sessionId().tradingDate(),
                        value.key().interval().startsAt(), value.key().interval().endsAt(), value.values().open(),
                        value.values().high(), value.values().low(), value.values().close(), value.values().volume(),
                        value.availableAt(), evidenceReference(value)))
                .toList();
        return analyzer.analyze(command.snapshot(), history, command.analysisCutoff());
    }

    private static String evidenceReference(org.predictiveedge.marketintelligence.domain.MarketBarRevision value) {
        return value.key().subject().id() + ":" + value.key().timeframe() + ":"
                + value.key().interval().startsAt() + ":r" + value.revision() + ":"
                + value.inputManifestHash().value();
    }

    public record Command(
            UUID userId,
            String brokerAccountId,
            PreOpenSnapshot snapshot,
            Instant analysisCutoff,
            Instant knowledgeCutoff,
            Duration historyWindow) {
        public Command {
            Objects.requireNonNull(userId, "User id is required");
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            brokerAccountId = brokerAccountId.trim();
            Objects.requireNonNull(snapshot, "Pre-open snapshot is required");
            Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
            Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
            if (analysisCutoff.isAfter(knowledgeCutoff))
                throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
            Objects.requireNonNull(historyWindow, "History window is required");
            if (historyWindow.compareTo(Duration.ofDays(7)) < 0 || historyWindow.compareTo(Duration.ofDays(180)) > 0)
                throw new IllegalArgumentException("History window must be between 7 and 180 days");
        }
    }

    public record StoredCommand(
            UUID userId,
            String brokerAccountId,
            String venue,
            String symbol,
            LocalDate tradingDate,
            Instant analysisCutoff,
            Instant knowledgeCutoff,
            Duration historyWindow) {
        public StoredCommand {
            Objects.requireNonNull(userId, "User id is required");
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            brokerAccountId = brokerAccountId.trim();
            if (venue == null || venue.isBlank()) throw new IllegalArgumentException("Venue is required");
            venue = venue.trim().toUpperCase(java.util.Locale.ROOT);
            if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
            symbol = symbol.trim().toUpperCase(java.util.Locale.ROOT);
            Objects.requireNonNull(tradingDate, "Trading date is required");
            Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
            Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
            if (analysisCutoff.isAfter(knowledgeCutoff))
                throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
            Objects.requireNonNull(historyWindow, "History window is required");
            if (historyWindow.compareTo(Duration.ofDays(7)) < 0 || historyWindow.compareTo(Duration.ofDays(180)) > 0)
                throw new IllegalArgumentException("History window must be between 7 and 180 days");
        }
    }
}
