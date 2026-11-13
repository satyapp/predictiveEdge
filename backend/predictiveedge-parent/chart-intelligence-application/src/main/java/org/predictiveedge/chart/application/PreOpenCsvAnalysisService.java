package org.predictiveedge.chart.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;
import org.predictiveedge.chart.domain.IntradayHistoricalBar;
import org.predictiveedge.chart.domain.IntradaySetupAction;
import org.predictiveedge.chart.domain.IntradaySetupAssessment;
import org.predictiveedge.chart.domain.PreOpenCandidateRanker;
import org.predictiveedge.chart.domain.PreOpenIntradayAnalyzer;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;

/** End-to-end NSE pre-open CSV screening with user-scoped Zerodha historical evidence. */
public final class PreOpenCsvAnalysisService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Duration ZERODHA_HISTORY_CHUNK = Duration.ofDays(90);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal MINIMUM_GAP_PERCENT = new BigDecimal("0.30");
    private static final BigDecimal MAXIMUM_GAP_PERCENT = new BigDecimal("2.50");
    private static final BigDecimal MAXIMUM_SPREAD_BPS = new BigDecimal("20");
    private static final BigDecimal MINIMUM_TURNOVER = new BigDecimal("5000000");

    private final NsePreOpenCsvParser parser;
    private final LiveMarketDataInstrumentResolver instruments;
    private final HistoricalMarketDataProvider history;
    private final PreOpenAuctionPublicationPort publications;
    private final PreOpenIntradayAnalyzer analyzer;
    private final PreOpenCandidateRanker ranker;
    private final Clock clock;

    public PreOpenCsvAnalysisService(
            NsePreOpenCsvParser parser,
            LiveMarketDataInstrumentResolver instruments,
            HistoricalMarketDataProvider history,
            PreOpenAuctionPublicationPort publications,
            PreOpenIntradayAnalyzer analyzer,
            PreOpenCandidateRanker ranker,
            Clock clock) {
        this.parser = Objects.requireNonNull(parser, "CSV parser is required");
        this.instruments = Objects.requireNonNull(instruments, "Instrument resolver is required");
        this.history = Objects.requireNonNull(history, "Historical-data provider is required");
        this.publications = Objects.requireNonNull(publications, "Snapshot publication port is required");
        this.analyzer = Objects.requireNonNull(analyzer, "Pre-open analyzer is required");
        this.ranker = Objects.requireNonNull(ranker, "Pre-open candidate ranker is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Result analyze(Command command) {
        Objects.requireNonNull(command, "CSV analysis command is required");
        Instant startedAt = clock.instant();
        if (startedAt.isBefore(command.observedAt()))
            throw new IllegalArgumentException("Observation timestamp cannot be in the future");
        var parsed = parser.parse(command.csv(), command.observedAt());
        List<PreOpenSnapshot> shortlisted = parsed.snapshots().stream()
                .filter(PreOpenCsvAnalysisService::passesPreFilter)
                .sorted(Comparator.comparing(PreOpenSnapshot::auctionTurnover).reversed()
                        .thenComparing(PreOpenSnapshot::symbol))
                .limit(command.maximumCandidates())
                .toList();
        var context = BrokerContext.withoutCredentials(command.userId(), command.brokerAccountId());
        var analyses = new ArrayList<InstrumentAnalysis>();
        var failures = new ArrayList<InstrumentFailure>();
        for (PreOpenSnapshot snapshot : shortlisted) {
            try {
                var resolved = instruments.resolve(context,
                        List.of(new Instrument(snapshot.venue(), snapshot.symbol()))).getFirst();
                if (resolved.kind() != MarketDataInstrumentKind.EQUITY)
                    throw new IllegalArgumentException("Zerodha instrument is not a cash equity");
                publications.publish(command.userId(), command.brokerAccountId(),
                        observation(snapshot, command.tradingDate()), startedAt);
                List<IntradayHistoricalBar> bars = fetchHistory(context, resolved.instrument(),
                        resolved.providerInstrumentId(), command.analysisCutoff().minus(command.historyWindow()),
                        command.analysisCutoff()).stream()
                        .map(candle -> new IntradayHistoricalBar(
                                LocalDate.ofInstant(candle.timestamp(), MARKET_ZONE), candle.timestamp(),
                                candle.timestamp().plus(Duration.ofMinutes(5)), candle.open(), candle.high(),
                                candle.low(), candle.close(), candle.volume(),
                                candle.timestamp().plus(Duration.ofMinutes(5)),
                                "ZERODHA:" + resolved.providerInstrumentId() + ":5m:" + candle.timestamp()))
                        .toList();
                IntradaySetupAssessment assessment = analyzer.analyze(snapshot, bars, command.analysisCutoff());
                analyses.add(new InstrumentAnalysis(resolved.providerInstrumentId(), bars.size(), assessment));
            } catch (RuntimeException failure) {
                failures.add(new InstrumentFailure(snapshot.venue(), snapshot.symbol(),
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
            }
        }
        List<InstrumentAnalysis> candidates = analyses.stream()
                .filter(value -> value.assessment().action() == IntradaySetupAction.BUY_CANDIDATE
                        || value.assessment().action() == IntradaySetupAction.SELL_CANDIDATE)
                .toList();
        var ranking = ranker.rank(candidates.stream().map(InstrumentAnalysis::assessment).toList(),
                command.maximumCandidates());
        return new Result("CANDIDATE_ONLY", startedAt, clock.instant(), parsed.snapshots().size(),
                parsed.rejectedRows(), shortlisted.size(), List.copyOf(analyses), candidates, ranking,
                List.copyOf(failures));
    }

    private List<Candle> fetchHistory(
            BrokerContext context, Instrument instrument, String providerInstrumentId, Instant from, Instant to) {
        var candles = new TreeMap<Instant, Candle>();
        Instant chunkFrom = from;
        while (chunkFrom.isBefore(to)) {
            Instant chunkTo = chunkFrom.plus(ZERODHA_HISTORY_CHUNK);
            if (chunkTo.isAfter(to)) chunkTo = to;
            var request = new HistoricalDataRequest(instrument, providerInstrumentId, CandleInterval.FIVE_MINUTES,
                    chunkFrom, chunkTo, false, false);
            history.historicalCandles(context, request).stream()
                    .filter(candle -> !candle.timestamp().isBefore(from) && candle.timestamp().isBefore(to))
                    .forEach(candle -> candles.put(candle.timestamp(), candle));
            chunkFrom = chunkTo;
        }
        return List.copyOf(candles.values());
    }

    private static boolean passesPreFilter(PreOpenSnapshot snapshot) {
        BigDecimal gap = snapshot.auctionPrice().subtract(snapshot.previousClose()).multiply(BigDecimal.valueOf(100), MC)
                .divide(snapshot.previousClose(), MC).abs();
        BigDecimal midpoint = snapshot.bestAsk().add(snapshot.bestBid()).divide(BigDecimal.valueOf(2), MC);
        BigDecimal spread = snapshot.bestAsk().subtract(snapshot.bestBid()).multiply(BigDecimal.valueOf(10_000), MC)
                .divide(midpoint, MC);
        return gap.compareTo(MINIMUM_GAP_PERCENT) >= 0 && gap.compareTo(MAXIMUM_GAP_PERCENT) <= 0
                && spread.compareTo(MAXIMUM_SPREAD_BPS) <= 0 && snapshot.auctionVolume() > 0
                && snapshot.auctionTurnover().compareTo(MINIMUM_TURNOVER) >= 0;
    }

    private static PreOpenAuctionObservation observation(PreOpenSnapshot value, LocalDate tradingDate) {
        return new PreOpenAuctionObservation(value.venue(), value.symbol(), value.series(), tradingDate,
                value.previousClose(), value.auctionPrice(), value.auctionVolume(), value.auctionTurnover(),
                value.bestBid(), value.bestAsk(), value.observedAt());
    }

    public record Command(
            UUID userId,
            String brokerAccountId,
            String csv,
            LocalDate tradingDate,
            Instant observedAt,
            Instant analysisCutoff,
            Duration historyWindow,
            int maximumCandidates) {
        public Command {
            Objects.requireNonNull(userId, "User id is required");
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            brokerAccountId = brokerAccountId.trim();
            if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Pre-open CSV is empty");
            Objects.requireNonNull(tradingDate, "Trading date is required");
            Objects.requireNonNull(observedAt, "Observation timestamp is required");
            Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
            if (observedAt.isAfter(analysisCutoff))
                throw new IllegalArgumentException("Observation timestamp cannot follow analysis cutoff");
            Objects.requireNonNull(historyWindow, "History window is required");
            if (historyWindow.compareTo(Duration.ofDays(7)) < 0 || historyWindow.compareTo(Duration.ofDays(365)) > 0)
                throw new IllegalArgumentException("History window must be between 7 and 365 days");
            if (maximumCandidates < 1 || maximumCandidates > 25)
                throw new IllegalArgumentException("Maximum candidates must be between 1 and 25");
        }
    }

    public record Result(
            String authority,
            Instant startedAt,
            Instant completedAt,
            int parsedSnapshots,
            List<NsePreOpenCsvParser.RowRejection> rejectedRows,
            int shortlistedForHistory,
            List<InstrumentAnalysis> analyses,
            List<InstrumentAnalysis> candidates,
            PreOpenCandidateRanker.RankedUniverse ranking,
            List<InstrumentFailure> failures) { }

    public record InstrumentAnalysis(
            String providerInstrumentId, int historicalBars, IntradaySetupAssessment assessment) { }

    public record InstrumentFailure(String venue, String symbol, String reason) { }
}
