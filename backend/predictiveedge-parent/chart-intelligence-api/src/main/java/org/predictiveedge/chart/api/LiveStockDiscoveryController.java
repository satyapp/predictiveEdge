package org.predictiveedge.chart.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.predictiveedge.chart.application.LiveMarketScreenAnalysisService;
import org.predictiveedge.chart.application.LiveMarketScreenSource;
import org.predictiveedge.chart.application.MarketScreenHistoricalAnalysisService;
import org.predictiveedge.chart.application.MarketScreenOllamaAnalysisPort;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.ScreenerRunRecorder;
import org.predictiveedge.chart.application.StageOneGoldenCopy;
import org.predictiveedge.chart.domain.MarketScreenType;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart-intelligence/v1/live-stock-discovery")
@ConditionalOnBean(LiveMarketScreenAnalysisService.class)
public class LiveStockDiscoveryController {
    private final LiveMarketScreenAnalysisService service;
    private final MarketScreenOllamaAnalysisPort ollama;
    private final AnalysisRunRecorder history;
    private final ScreenerRunRecorder runs;
    private final ObjectMapper json;

    public LiveStockDiscoveryController(LiveMarketScreenAnalysisService service,
            MarketScreenOllamaAnalysisPort ollama, AnalysisRunRecorder history, ScreenerRunRecorder runs,
            ObjectMapper json) {
        this.service = service; this.ollama = ollama; this.history = history; this.runs = runs; this.json = json;
    }

    @GetMapping("/capabilities")
    public List<LiveMarketScreenSource.Capability> capabilities() { return service.capabilities(); }

    @PostMapping("/search")
    public SearchResponse search(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        var dataCopy = service.search(command(identity, request));
        var run = record(identity, request, dataCopy);
        return new SearchResponse("BROKER_SCREEN_ONLY", dataCopy, run.searchId());
    }

    @PostMapping("/ollama")
    public Response analyze(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        var live = service.analyze(command(identity, request));
        var run = record(identity, request, live.dataCopy());
        var stageOne = ollama.analyze(identity.user().id(), request.brokerAccountId(), live.deterministic(), run);
        var response = new Response("PRELIMINARY_NOT_FINAL", live.dataCopy(), live.deterministic(),
                stageOne.analysis(), stageOne.goldenCopy(), run.searchId());
        String mode = request.selectedSymbol().isBlank() ? "MARKET_SCREEN_FULL" : "CONSOLIDATED_LEVEL_ONE";
        history.record(identity.user().id(), request.screenType().name(), mode,
                live.dataCopy().source(), serialize(live.dataCopy()), request.tradingDate(),
                response.ollama().candidates().size(), response.ollama().model(), response);
        return response;
    }

    private static LiveMarketScreenAnalysisService.Command command(AuthenticatedIdentity identity, Request request) {
        return new LiveMarketScreenAnalysisService.Command(identity.user().id(), request.brokerAccountId(),
                request.screenType(), request.tradingDate(), Instant.now(), Duration.ofDays(request.historyDays()),
                request.maximumCandidates(), request.setup(), request.universe(), request.timeframe(),
                request.selectedSymbol());
    }

    /** Saves the screener run before any of its stocks are selected, so every selection has a parent run. */
    private ScreenerRunRecorder.ScreenerRun record(AuthenticatedIdentity identity, Request request,
            org.predictiveedge.chart.application.MarketScreenDataCopy dataCopy) {
        return runs.record(identity.user().id(), request.brokerAccountId(), request.tradingDate(), dataCopy,
                request.setup(), request.universe(), request.timeframe(), serialize(request), this::serialize);
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Live data copy could not be persisted", failure); }
    }

    public record Request(@NotBlank String brokerAccountId, @NotNull MarketScreenType screenType,
            @NotNull LocalDate tradingDate, @Min(7) @Max(365) int historyDays,
            @Min(1) @Max(20) int maximumCandidates, String setup, String universe,
            String timeframe, String selectedSymbol) {
        public Request {
            if (historyDays == 0) historyDays = 365;
            if (maximumCandidates == 0) maximumCandidates = 10;
            setup = setup == null ? "" : setup.trim();
            universe = universe == null ? "" : universe.trim();
            timeframe = timeframe == null ? "" : timeframe.trim();
            selectedSymbol = selectedSymbol == null ? "" : selectedSymbol.trim();
        }
    }

    public record Response(String authority,
            org.predictiveedge.chart.application.MarketScreenDataCopy dataCopy,
            MarketScreenHistoricalAnalysisService.Result deterministic,
            PreOpenOllamaAnalysis ollama, StageOneGoldenCopy goldenCopy, UUID searchId) { }

    public record SearchResponse(String authority,
            org.predictiveedge.chart.application.MarketScreenDataCopy dataCopy, UUID searchId) { }
}
