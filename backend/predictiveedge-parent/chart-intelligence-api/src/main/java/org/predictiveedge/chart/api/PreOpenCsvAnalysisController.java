package org.predictiveedge.chart.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.predictiveedge.chart.application.PreOpenCsvAnalysisService;
import org.predictiveedge.chart.application.ScreenerRunRecorder;
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

/**
 * Deterministic pre-open analysis of an NSE pre-open CSV. The day's rows are stored as a PRE_OPEN run so that each
 * stock can be given its pre-open row as evidence. Ollama does not read or rank the CSV.
 */
@Validated
@RestController
@RequestMapping("/api/chart-intelligence/v1/pre-open-csv-analyses")
@ConditionalOnBean(PreOpenCsvAnalysisService.class)
public class PreOpenCsvAnalysisController {
    private static final long MAXIMUM_FILE_BYTES = 2L * 1024 * 1024;
    private final PreOpenCsvAnalysisService service;
    private final AnalysisRunRecorder history;
    private final ScreenerRunRecorder runs;
    private final ObjectMapper json;

    public PreOpenCsvAnalysisController(PreOpenCsvAnalysisService service, AnalysisRunRecorder history,
            ScreenerRunRecorder runs, ObjectMapper json) {
        this.service = service;
        this.history = history;
        this.runs = runs;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response analyze(
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
            var deterministic = service.analyze(new PreOpenCsvAnalysisService.Command(identity.user().id(),
                    brokerAccountId, csv, tradingDate, observedAt, analysisCutoff, Duration.ofDays(historyDays),
                    maximumCandidates));
            var run = runs.recordPreOpen(identity.user().id(), brokerAccountId, tradingDate,
                    deterministic.dayRows(), criteria(brokerAccountId, tradingDate, historyDays, maximumCandidates),
                    this::serialize, deterministic.startedAt());
            var result = new Response("PRELIMINARY_NOT_FINAL", deterministic, run.searchId());
            history.record(identity.user().id(), "PRE_OPEN", "PRE_OPEN_DETERMINISTIC", file.getOriginalFilename(),
                    csv, tradingDate, result.deterministic().candidates().size(),
                    result.deterministic().ranking().scoreVersion(), result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Pre-open CSV file could not be read", failure);
        }
    }

    private String criteria(String brokerAccountId, LocalDate tradingDate, int historyDays, int maximumCandidates) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("brokerAccountId", brokerAccountId);
        criteria.put("tradingDate", tradingDate.toString());
        criteria.put("historyDays", historyDays);
        criteria.put("maximumCandidates", maximumCandidates);
        return serialize(criteria);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Pre-open run could not be persisted", failure);
        }
    }

    /** The deterministic analysis of the day, and the id of the stored PRE_OPEN run that holds its rows. */
    public record Response(String authority, PreOpenCsvAnalysisService.Result deterministic, UUID searchId) { }
}
