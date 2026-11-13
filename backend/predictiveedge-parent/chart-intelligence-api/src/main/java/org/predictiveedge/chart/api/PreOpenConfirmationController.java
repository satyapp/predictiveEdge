package org.predictiveedge.chart.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.predictiveedge.chart.application.PreOpenCandidateConfirmationService;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart-intelligence/v1/pre-open-confirmations")
@ConditionalOnBean(PreOpenCandidateConfirmationService.class)
public class PreOpenConfirmationController {
    private final PreOpenCandidateConfirmationService service;

    public PreOpenConfirmationController(PreOpenCandidateConfirmationService service) {
        this.service = service;
    }

    @PostMapping
    public Response confirm(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        List<InstrumentResult> results = request.instruments().stream().map(instrument ->
                new InstrumentResult(instrument.venue(), instrument.symbol(), service.confirm(
                        new PreOpenCandidateConfirmationService.Command(identity.user().id(),
                                request.brokerAccountId(), instrument.venue(), instrument.symbol(),
                                request.tradingDate(), request.analysisCutoff(), request.knowledgeCutoff(),
                                Duration.ofDays(request.historyDays()))))).toList();
        return new Response("CHART_EVIDENCE_ONLY", request.analysisCutoff(), request.knowledgeCutoff(), results);
    }

    public record Request(@NotBlank String brokerAccountId, @NotNull LocalDate tradingDate,
            @NotNull Instant analysisCutoff, @NotNull Instant knowledgeCutoff,
            @Min(7) @Max(365) int historyDays,
            @NotEmpty @Size(max = 25) List<@Valid InstrumentRequest> instruments) { }
    public record InstrumentRequest(@NotBlank String venue, @NotBlank String symbol) { }
    public record InstrumentResult(String venue, String symbol,
            PreOpenCandidateConfirmationService.Result result) { }
    public record Response(String authority, Instant analysisCutoff, Instant knowledgeCutoff,
            List<InstrumentResult> results) { }
}
