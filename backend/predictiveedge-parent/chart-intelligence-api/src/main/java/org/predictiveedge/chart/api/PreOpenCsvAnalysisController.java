package org.predictiveedge.chart.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.predictiveedge.chart.application.PreOpenCsvAnalysisService;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysisPort;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/chart-intelligence/v1/pre-open-csv-analyses")
@ConditionalOnBean(PreOpenCsvAnalysisService.class)
public class PreOpenCsvAnalysisController {
    private static final long MAXIMUM_FILE_BYTES = 2L * 1024 * 1024;
    private final PreOpenCsvAnalysisService service;
    private final PreOpenOllamaAnalysisPort ollama;
    private final AnalysisRunRecorder history;

    public PreOpenCsvAnalysisController(PreOpenCsvAnalysisService service, PreOpenOllamaAnalysisPort ollama,
            AnalysisRunRecorder history) {
        this.service = service;
        this.ollama = ollama;
        this.history = history;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreOpenCsvAnalysisService.Result analyze(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotBlank String brokerAccountId,
            @RequestParam @NotNull LocalDate tradingDate,
            @RequestParam @NotNull Instant observedAt,
            @RequestParam @NotNull Instant analysisCutoff,
            @RequestParam(defaultValue = "365") @Min(7) @Max(365) int historyDays,
            @RequestParam(defaultValue = "10") @Min(1) @Max(25) int maximumCandidates,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Pre-open CSV file is required");
        if (file.getSize() > MAXIMUM_FILE_BYTES)
            throw new IllegalArgumentException("Pre-open CSV file must not exceed 2 MB");
        try {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            var result = service.analyze(new PreOpenCsvAnalysisService.Command(identity.user().id(), brokerAccountId,
                    csv, tradingDate, observedAt, analysisCutoff, Duration.ofDays(historyDays), maximumCandidates));
            history.record(identity.user().id(), "PRE_OPEN", "PRE_OPEN_DETERMINISTIC", file.getOriginalFilename(),
                    csv, tradingDate, result.candidates().size(), result.ranking().scoreVersion(), result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Pre-open CSV file could not be read", failure);
        }
    }

    @PostMapping(path = "/ollama", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OllamaResponse analyzeWithOllama(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotBlank String brokerAccountId,
            @RequestParam @NotNull LocalDate tradingDate,
            @RequestParam @NotNull Instant observedAt,
            @RequestParam @NotNull Instant analysisCutoff,
            @RequestParam(defaultValue = "365") @Min(7) @Max(365) int historyDays,
            @RequestParam(defaultValue = "10") @Min(1) @Max(15) int maximumCandidates,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Pre-open CSV file is required");
        if (file.getSize() > MAXIMUM_FILE_BYTES)
            throw new IllegalArgumentException("Pre-open CSV file must not exceed 2 MB");
        try {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            var deterministic = service.analyze(new PreOpenCsvAnalysisService.Command(identity.user().id(),
                    brokerAccountId, csv, tradingDate, observedAt,
                    analysisCutoff, Duration.ofDays(historyDays), maximumCandidates));
            var result = new OllamaResponse("PRELIMINARY_NOT_FINAL", deterministic, ollama.analyze(deterministic));
            history.record(identity.user().id(), "PRE_OPEN", "PRE_OPEN_FULL", file.getOriginalFilename(), csv,
                    tradingDate, result.ollama().candidates().size(), result.ollama().model(), result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Pre-open CSV file could not be read", failure);
        }
    }

    public record OllamaResponse(String authority, PreOpenCsvAnalysisService.Result deterministic,
            PreOpenOllamaAnalysis ollama) { }
}
