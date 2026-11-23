package org.predictiveedge.chart.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Changes only intake: NSE MCP is normalized, then the existing analysis service is reused. */
public final class LiveMarketScreenAnalysisService {
    private final LiveMarketScreenSource source;
    private final MarketScreenHistoricalAnalysisService downstream;

    public LiveMarketScreenAnalysisService(LiveMarketScreenSource source,
            MarketScreenHistoricalAnalysisService downstream) {
        this.source = Objects.requireNonNull(source);
        this.downstream = Objects.requireNonNull(downstream);
    }

    public List<LiveMarketScreenSource.Capability> capabilities() { return source.capabilities(); }

    /** Fetches broker screener rows only. Historical and AI analysis must be requested separately. */
    public MarketScreenDataCopy search(Command command) {
        Objects.requireNonNull(command, "Live market-screen command is required");
        return source.fetch(new LiveMarketScreenSource.FetchRequest(
                command.userId(), command.screenType(), command.setup(), command.universe(), command.timeframe(),
                command.maximumCandidates(), command.selectedSymbol()));
    }

    public Result analyze(Command command) {
        Objects.requireNonNull(command, "Live market-screen command is required");
        MarketScreenDataCopy dataCopy = search(command);
        Instant effectiveCutoff = command.analysisCutoff().isBefore(dataCopy.observedAt())
                ? dataCopy.observedAt() : command.analysisCutoff();
        var deterministic = downstream.analyzeCanonical(new MarketScreenHistoricalAnalysisService.CanonicalCommand(
                command.userId(), command.brokerAccountId(), dataCopy, command.tradingDate(),
                effectiveCutoff, command.historyWindow(), command.maximumCandidates()));
        return new Result(dataCopy, deterministic);
    }

    public record Command(UUID userId, String brokerAccountId, MarketScreenType screenType,
            LocalDate tradingDate, Instant analysisCutoff, Duration historyWindow, int maximumCandidates,
            String setup, String universe, String timeframe, String selectedSymbol) {
        public Command {
            Objects.requireNonNull(userId); Objects.requireNonNull(screenType); Objects.requireNonNull(tradingDate);
            Objects.requireNonNull(analysisCutoff); Objects.requireNonNull(historyWindow);
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            if (maximumCandidates < 1 || maximumCandidates > 20)
                throw new IllegalArgumentException("Maximum candidates must be between 1 and 20");
            setup = setup == null ? "" : setup.trim();
            universe = universe == null ? "" : universe.trim();
            timeframe = timeframe == null ? "" : timeframe.trim();
            selectedSymbol = selectedSymbol == null ? "" : selectedSymbol.trim();
        }
    }

    public record Result(MarketScreenDataCopy dataCopy,
            MarketScreenHistoricalAnalysisService.Result deterministic) { }
}
