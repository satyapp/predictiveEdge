package org.predictiveedge.chart.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/chart-intelligence/v1/analysis-history")
public class AnalysisHistoryController {
    private final AnalysisRunRecorder recorder;

    public AnalysisHistoryController(AnalysisRunRecorder recorder) {
        this.recorder = recorder;
    }

    @GetMapping
    public List<AnalysisRunHistoryStore.AnalysisRunSummary> recent(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return recorder.recent(identity.user().id(), limit);
    }

    @GetMapping("/{runId}")
    public AnalysisRunRecorder.AnalysisRunDetail find(
            @AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID runId) {
        return recorder.find(identity.user().id(), runId);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AnalysisRunRecorder.AnalysisRunNotFoundException.class)
    void notFound() { }
}
