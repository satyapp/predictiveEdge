package org.predictiveedge.chart.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.CandleInterval;
import org.predictiveedge.broker.domain.HistoricalDataRequest;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;
import org.predictiveedge.chart.domain.IntradayHistoricalBar;
import org.predictiveedge.chart.domain.MarketScreenCandidateRanker;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenTechnicalAnalyzer;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Enriches a ranked NSE market screen with point-in-time Zerodha history and governed technical levels. */
public final class MarketScreenHistoricalAnalysisService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Duration HISTORY_CHUNK = Duration.ofDays(90);
    private final MarketScreenCsvParserRegistry parsers;
    private final MarketScreenCandidateRanker ranker;
    private final LiveMarketDataInstrumentResolver instruments;
    private final HistoricalMarketDataProvider history;
    private final MarketScreenTechnicalAnalyzer analyzer;
    private final Clock clock;

    public MarketScreenHistoricalAnalysisService(MarketScreenCsvParserRegistry parsers,
            MarketScreenCandidateRanker ranker, LiveMarketDataInstrumentResolver instruments,
            HistoricalMarketDataProvider history, MarketScreenTechnicalAnalyzer analyzer, Clock clock) {
        this.parsers = Objects.requireNonNull(parsers); this.ranker = Objects.requireNonNull(ranker);
        this.instruments = Objects.requireNonNull(instruments); this.history = Objects.requireNonNull(history);
        this.analyzer = Objects.requireNonNull(analyzer); this.clock = Objects.requireNonNull(clock);
    }

    public Result analyze(Command command) {
        Objects.requireNonNull(command, "Market-screen command is required");
        var parsed = parsers.parse(command.screenType(), command.csv(), command.observedAt());
        var ranking = ranker.rank(parsed.observations());
        var bySymbol = parsed.observations().stream().collect(Collectors.toMap(MarketScreenObservation::symbol,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
        var context = BrokerContext.withoutCredentials(command.userId(), command.brokerAccountId());
        var analyses = new ArrayList<InstrumentAnalysis>();
        var failures = new ArrayList<InstrumentFailure>();
        ranking.candidates().stream().limit(command.maximumCandidates()).forEach(candidate -> {
            MarketScreenObservation observation = bySymbol.get(candidate.symbol());
            try {
                var resolved = instruments.resolve(context,
                        List.of(new Instrument(observation.venue(), observation.symbol()))).getFirst();
                if (resolved.kind() != MarketDataInstrumentKind.EQUITY)
                    throw new IllegalArgumentException("Zerodha instrument is not a cash equity");
                List<IntradayHistoricalBar> bars = fetchHistory(context, resolved.instrument(),
                        resolved.providerInstrumentId(), command.analysisCutoff().minus(command.historyWindow()),
                        command.analysisCutoff()).stream().map(candle -> new IntradayHistoricalBar(
                                LocalDate.ofInstant(candle.timestamp(), MARKET_ZONE), candle.timestamp(),
                                candle.timestamp().plus(Duration.ofMinutes(5)), candle.open(), candle.high(),
                                candle.low(), candle.close(), candle.volume(), candle.timestamp().plus(Duration.ofMinutes(5)),
                                "ZERODHA:" + resolved.providerInstrumentId() + ":5m:" + candle.timestamp())).toList();
                analyses.add(new InstrumentAnalysis(resolved.providerInstrumentId(), bars.size(), observation,
                        analyzer.analyze(observation, bars, command.analysisCutoff())));
            } catch (RuntimeException failure) {
                failures.add(new InstrumentFailure(observation.venue(), observation.symbol(),
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
            }
        });
        return new Result("PRELIMINARY_NOT_FINAL", command.screenType(), clock.instant(),
                parsed.observations().size(), parsed.rejectedRows(), ranking, List.copyOf(analyses),
                List.copyOf(failures));
    }

    private List<Candle> fetchHistory(BrokerContext context, Instrument instrument, String providerInstrumentId,
            Instant from, Instant to) {
        var candles = new TreeMap<Instant, Candle>(); Instant chunkFrom = from;
        while (chunkFrom.isBefore(to)) {
            Instant chunkTo = chunkFrom.plus(HISTORY_CHUNK); if (chunkTo.isAfter(to)) chunkTo = to;
            var request = new HistoricalDataRequest(instrument, providerInstrumentId, CandleInterval.FIVE_MINUTES,
                    chunkFrom, chunkTo, false, false);
            history.historicalCandles(context, request).stream()
                    .filter(value -> !value.timestamp().isBefore(from) && value.timestamp().isBefore(to))
                    .forEach(value -> candles.put(value.timestamp(), value));
            chunkFrom = chunkTo;
        }
        return List.copyOf(candles.values());
    }

    public record Command(UUID userId, String brokerAccountId, MarketScreenType screenType, String csv,
            LocalDate tradingDate, Instant observedAt, Instant analysisCutoff, Duration historyWindow,
            int maximumCandidates) {
        public Command {
            Objects.requireNonNull(userId); Objects.requireNonNull(screenType); Objects.requireNonNull(tradingDate);
            Objects.requireNonNull(observedAt); Objects.requireNonNull(analysisCutoff); Objects.requireNonNull(historyWindow);
            if (brokerAccountId == null || brokerAccountId.isBlank()) throw new IllegalArgumentException("Broker account id is required");
            if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Market-screen CSV is empty");
            if (observedAt.isAfter(analysisCutoff)) throw new IllegalArgumentException("Observation cannot follow cutoff");
            if (historyWindow.compareTo(Duration.ofDays(7)) < 0 || historyWindow.compareTo(Duration.ofDays(365)) > 0)
                throw new IllegalArgumentException("History window must be between 7 and 365 days");
            if (maximumCandidates < 1 || maximumCandidates > 20)
                throw new IllegalArgumentException("Maximum candidates must be between 1 and 20");
        }
    }

    public record Result(String authority, MarketScreenType screenType, Instant analyzedAt, int parsedRows,
            List<MarketScreenCsvParser.RowRejection> rejectedRows, MarketScreenCandidateRanker.RankedScreen ranking,
            List<InstrumentAnalysis> analyses, List<InstrumentFailure> failures) { }
    public record InstrumentAnalysis(String providerInstrumentId, int historicalBars,
            MarketScreenObservation observation, org.predictiveedge.chart.domain.MarketScreenTechnicalAssessment assessment) { }
    public record InstrumentFailure(String venue, String symbol, String reason) { }
}
