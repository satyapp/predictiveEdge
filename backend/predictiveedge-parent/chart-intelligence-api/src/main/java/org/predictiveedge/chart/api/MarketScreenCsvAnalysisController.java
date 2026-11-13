package org.predictiveedge.chart.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.predictiveedge.chart.application.MarketScreenCsvAnalysisService;
import org.predictiveedge.chart.application.MarketScreenHistoricalAnalysisService;
import org.predictiveedge.chart.application.MarketScreenOllamaAnalysisPort;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.domain.MarketScreenType;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chart-intelligence/v1/market-screen-csv-analyses")
@ConditionalOnBean(MarketScreenCsvAnalysisService.class)
public class MarketScreenCsvAnalysisController {
    private static final long MAXIMUM_FILE_BYTES = 2L * 1024 * 1024;
    private final MarketScreenCsvAnalysisService service;
    private final MarketScreenHistoricalAnalysisService historical;
    private final MarketScreenOllamaAnalysisPort ollama;
    private final AnalysisRunRecorder history;

    public MarketScreenCsvAnalysisController(MarketScreenCsvAnalysisService service,
            MarketScreenHistoricalAnalysisService historical, MarketScreenOllamaAnalysisPort ollama,
            AnalysisRunRecorder history) {
        this.service = service;
        this.historical = historical;
        this.ollama = ollama;
        this.history = history;
    }

    @GetMapping("/supported-types")
    public List<MarketScreenType> supportedTypes() { return service.supportedTypes(); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MarketScreenCsvAnalysisService.Result analyze(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotNull MarketScreenType screenType,
            @RequestParam @NotNull Instant observedAt,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Market-screen CSV file is required");
        if (file.getSize() > MAXIMUM_FILE_BYTES)
            throw new IllegalArgumentException("Market-screen CSV file must not exceed 2 MB");
        try {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            var result = service.analyze(new MarketScreenCsvAnalysisService.Command(screenType, csv, observedAt));
            history.record(identity.user().id(), screenType.name(), "MARKET_SCREEN", file.getOriginalFilename(),
                    csv, LocalDate.ofInstant(observedAt, java.time.ZoneId.of("Asia/Kolkata")),
                    result.ranking().candidates().size(), result.ranking().scoreVersion(), result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Market-screen CSV file could not be read", failure);
        }
    }

    @PostMapping(path = "/ollama", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OllamaResponse analyzeWithHistoryAndOllama(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotBlank String brokerAccountId,
            @RequestParam @NotNull MarketScreenType screenType,
            @RequestParam @NotNull LocalDate tradingDate,
            @RequestParam @NotNull Instant observedAt,
            @RequestParam @NotNull Instant analysisCutoff,
            @RequestParam(defaultValue = "365") @Min(7) @Max(365) int historyDays,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int maximumCandidates,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Market-screen CSV file is required");
        if (file.getSize() > MAXIMUM_FILE_BYTES)
            throw new IllegalArgumentException("Market-screen CSV file must not exceed 2 MB");
        try {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            var deterministic = historical.analyze(new MarketScreenHistoricalAnalysisService.Command(
                    identity.user().id(), brokerAccountId, screenType, csv, tradingDate, observedAt,
                    analysisCutoff, Duration.ofDays(historyDays), maximumCandidates));
            var result = new OllamaResponse("PRELIMINARY_NOT_FINAL", deterministic, ollama.analyze(deterministic));
            history.record(identity.user().id(), screenType.name(), "MARKET_SCREEN_FULL", file.getOriginalFilename(),
                    csv, tradingDate, result.ollama().candidates().size(), result.ollama().model(), result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Market-screen CSV file could not be read", failure);
        }
    }

    public record OllamaResponse(String authority, MarketScreenHistoricalAnalysisService.Result deterministic,
            PreOpenOllamaAnalysis ollama) { }
}
