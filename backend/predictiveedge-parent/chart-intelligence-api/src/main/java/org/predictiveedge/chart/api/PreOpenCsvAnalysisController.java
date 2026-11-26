package org.predictiveedge.chart.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.predictiveedge.chart.application.NsePreOpenCsvParser;
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
 * Saves the valid rows of an NSE pre-open CSV as a PRE_OPEN run. The selected stocks of a top-gainers search read
 * their rows from that run as stage-1 evidence. This endpoint does not shortlist, check history or call any model.
 */
@Validated
@RestController
@RequestMapping("/api/chart-intelligence/v1/pre-open-csv-analyses")
@ConditionalOnBean(PreOpenCsvAnalysisService.class)
public class PreOpenCsvAnalysisController {
    private static final long MAXIMUM_FILE_BYTES = 10L * 1024 * 1024;
    private final PreOpenCsvAnalysisService service;
    private final ScreenerRunRecorder runs;
    private final ObjectMapper json;

    public PreOpenCsvAnalysisController(PreOpenCsvAnalysisService service, ScreenerRunRecorder runs,
            ObjectMapper json) {
        this.service = service;
        this.runs = runs;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response save(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam @NotBlank String brokerAccountId,
            @RequestParam @NotNull LocalDate tradingDate,
            @RequestParam @NotNull Instant observedAt,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Pre-open CSV file is required");
        if (file.getSize() > MAXIMUM_FILE_BYTES)
            throw new IllegalArgumentException("Pre-open CSV file must not exceed 10 MB");
        try {
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            var parsed = service.ingest(new PreOpenCsvAnalysisService.Command(identity.user().id(), brokerAccountId,
                    csv, tradingDate, observedAt));
            var run = runs.recordPreOpen(identity.user().id(), brokerAccountId, tradingDate, parsed.validRows(),
                    criteria(brokerAccountId, tradingDate), this::serialize, parsed.startedAt());
            return new Response("PRELIMINARY_NOT_FINAL", parsed.parsedRows(), parsed.validRows().size(),
                    parsed.rejectedRows(), run.searchId());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Pre-open CSV file could not be read", failure);
        }
    }

    private String criteria(String brokerAccountId, LocalDate tradingDate) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("brokerAccountId", brokerAccountId);
        criteria.put("tradingDate", tradingDate.toString());
        return serialize(criteria);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Pre-open run could not be persisted", failure);
        }
    }

    /** How many rows the file held, how many were saved and rejected, and the id of the saved PRE_OPEN run. */
    public record Response(
            String authority,
            int parsedRows,
            int savedRows,
            List<NsePreOpenCsvParser.RowRejection> rejectedRows,
            UUID searchId) { }
}
