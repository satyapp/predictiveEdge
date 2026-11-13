package org.predictiveedge.chart.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.predictiveedge.chart.application.PreOpenIntradayAnalysisService;
import org.predictiveedge.chart.domain.IntradaySetupAssessment;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart-intelligence/v1/pre-open-analyses")
@ConditionalOnBean(PreOpenIntradayAnalysisService.class)
public class PreOpenIntradayAnalysisController {
    private final PreOpenIntradayAnalysisService service;

    public PreOpenIntradayAnalysisController(PreOpenIntradayAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public Response analyze(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        List<IntradaySetupAssessment> assessments = request.snapshots().stream().map(snapshot -> service.analyze(
                new PreOpenIntradayAnalysisService.Command(identity.user().id(), request.brokerAccountId(),
                        snapshot.toDomain(), request.analysisCutoff(), request.knowledgeCutoff(),
                        Duration.ofDays(request.historyDays())))).toList();
        return new Response("CANDIDATE_ONLY", request.analysisCutoff(), request.knowledgeCutoff(), assessments);
    }

    @PostMapping("/stored")
    public StoredResponse analyzeStored(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody StoredRequest request) {
        List<StoredResult> results = request.instruments().stream().map(instrument -> service.analyzeStored(
                new PreOpenIntradayAnalysisService.StoredCommand(identity.user().id(), request.brokerAccountId(),
                        instrument.venue(), instrument.symbol(), request.tradingDate(), request.analysisCutoff(),
                        request.knowledgeCutoff(), Duration.ofDays(request.historyDays())))
                .map(stored -> new StoredResult(instrument.venue(), instrument.symbol(), "ANALYZED",
                        stored.snapshot().snapshotId().toString(), stored.snapshot().evidenceHash(),
                        stored.snapshot().observation().observedAt(), stored.snapshot().receivedAt(),
                        stored.assessment()))
                .orElseGet(() -> new StoredResult(instrument.venue(), instrument.symbol(),
                        "SNAPSHOT_NOT_FOUND", null, null, null, null, null))).toList();
        return new StoredResponse("CANDIDATE_ONLY", request.analysisCutoff(), request.knowledgeCutoff(), results);
    }

    public record Request(
            @NotBlank String brokerAccountId,
            @NotNull Instant analysisCutoff,
            @NotNull Instant knowledgeCutoff,
            @Min(7) @Max(365) int historyDays,
            @NotEmpty @Size(max = 25) List<@Valid SnapshotRequest> snapshots) { }

    public record SnapshotRequest(
            @NotBlank String venue,
            @NotBlank String symbol,
            @NotBlank String series,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal previousClose,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal auctionPrice,
            @PositiveOrZero long auctionVolume,
            @NotNull @PositiveOrZero BigDecimal auctionTurnover,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal bestBid,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal bestAsk,
            @NotNull Instant observedAt) {
        PreOpenSnapshot toDomain() {
            return new PreOpenSnapshot(venue, symbol, series, previousClose, auctionPrice, auctionVolume,
                    auctionTurnover, bestBid, bestAsk, observedAt);
        }
    }

    public record Response(
            String authority,
            Instant analysisCutoff,
            Instant knowledgeCutoff,
            List<IntradaySetupAssessment> assessments) { }

    public record StoredRequest(
            @NotBlank String brokerAccountId,
            @NotNull LocalDate tradingDate,
            @NotNull Instant analysisCutoff,
            @NotNull Instant knowledgeCutoff,
            @Min(7) @Max(365) int historyDays,
            @NotEmpty @Size(max = 25) List<@Valid InstrumentRequest> instruments) { }

    public record InstrumentRequest(@NotBlank String venue, @NotBlank String symbol) { }

    public record StoredResponse(
            String authority,
            Instant analysisCutoff,
            Instant knowledgeCutoff,
            List<StoredResult> results) { }

    public record StoredResult(
            String venue,
            String symbol,
            String status,
            String snapshotId,
            String snapshotEvidenceHash,
            Instant snapshotObservedAt,
            Instant snapshotReceivedAt,
            IntradaySetupAssessment assessment) { }
}
