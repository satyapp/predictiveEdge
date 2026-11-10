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

    public PreOpenCsvAnalysisController(PreOpenCsvAnalysisService service) {
        this.service = service;
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
            return service.analyze(new PreOpenCsvAnalysisService.Command(identity.user().id(), brokerAccountId,
                    csv, tradingDate, observedAt, analysisCutoff, Duration.ofDays(historyDays), maximumCandidates));
        } catch (IOException failure) {
            throw new IllegalArgumentException("Pre-open CSV file could not be read", failure);
        }
    }
}
