package org.predictiveedge.guardian.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.predictiveedge.guardian.application.TradeGuardianService;
import org.predictiveedge.guardian.domain.ManualFill;
import org.predictiveedge.guardian.domain.MonitoringState;
import org.predictiveedge.guardian.domain.TradeDirection;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/trade-guardian/v1/monitoring-cases")
public class TradeGuardianController {
    private final TradeGuardianService guardian;

    public TradeGuardianController(TradeGuardianService guardian) {
        this.guardian = guardian;
    }

    @PostMapping
    public ResponseEntity<MonitoringCaseResponse> register(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody RegisterManualTradeRequest request) {
        TradeMonitoringCase registered = guardian.registerManualTrade(new TradeGuardianService.RegisterManualTrade(
                identity.user().id(), request.recommendationId(), request.approvedTradePlanRef(),
                request.accountRef(), request.venue(), request.symbol(), request.direction(), request.quantity(),
                request.averageEntryPrice(), request.executedAt(), request.externalExecutionRef()));
        return ResponseEntity.created(URI.create("/api/trade-guardian/v1/monitoring-cases/"
                + registered.monitoringCaseId())).body(MonitoringCaseResponse.from(registered));
    }

    @GetMapping("/{monitoringCaseId}")
    public MonitoringCaseResponse get(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID monitoringCaseId) {
        return MonitoringCaseResponse.from(guardian.monitoringCase(identity.user().id(), monitoringCaseId));
    }

    @PostMapping("/{monitoringCaseId}/actions/suspend")
    public MonitoringCaseResponse suspend(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID monitoringCaseId,
            @Valid @RequestBody SuspendMonitoringRequest request) {
        return MonitoringCaseResponse.from(
                guardian.suspendMonitoring(identity.user().id(), monitoringCaseId, request.reason()));
    }

    @PostMapping("/{monitoringCaseId}/actions/resume")
    public MonitoringCaseResponse resume(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID monitoringCaseId) {
        return MonitoringCaseResponse.from(guardian.resumeMonitoring(identity.user().id(), monitoringCaseId));
    }

    @PostMapping("/{monitoringCaseId}/actions/complete")
    public MonitoringCaseResponse complete(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID monitoringCaseId,
            @Valid @RequestBody CompleteManualTradeRequest request) {
        return MonitoringCaseResponse.from(guardian.completeManualTrade(
                new TradeGuardianService.CompleteManualTrade(identity.user().id(), monitoringCaseId,
                        request.quantity(), request.averageExitPrice(), request.executedAt(),
                        request.externalExecutionRef())));
    }

    public record RegisterManualTradeRequest(
            @NotBlank @Size(max = 256) String recommendationId,
            @NotBlank @Size(max = 512) String approvedTradePlanRef,
            @NotBlank @Size(max = 256) String accountRef,
            @NotBlank @Size(max = 64) String venue,
            @NotBlank @Size(max = 128) String symbol,
            @NotNull TradeDirection direction,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal averageEntryPrice,
            @NotNull @PastOrPresent Instant executedAt,
            @Size(max = 256) String externalExecutionRef) {}

    public record SuspendMonitoringRequest(@NotBlank @Size(max = 256) String reason) {}

    public record CompleteManualTradeRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal averageExitPrice,
            @NotNull @PastOrPresent Instant executedAt,
            @Size(max = 256) String externalExecutionRef) {}

    public record MonitoringCaseResponse(
            UUID monitoringCaseId,
            UUID tradeId,
            String recommendationId,
            String approvedTradePlanRef,
            String accountRef,
            String venue,
            String symbol,
            TradeDirection direction,
            FillResponse entryFill,
            MonitoringState state,
            long version,
            Instant registeredAt,
            Instant stateChangedAt,
            String suspensionReason,
            FillResponse exitFill,
            boolean advisoryOnly) {

        static MonitoringCaseResponse from(TradeMonitoringCase value) {
            return new MonitoringCaseResponse(value.monitoringCaseId(), value.tradeId(), value.recommendationId(),
                    value.approvedTradePlanRef(), value.accountRef(), value.instrument().venue(),
                    value.instrument().symbol(), value.direction(), FillResponse.from(value.entryFill()),
                    value.state(), value.version(), value.registeredAt(), value.stateChangedAt(),
                    value.suspensionReason(), value.exitFill() == null ? null : FillResponse.from(value.exitFill()),
                    true);
        }
    }

    public record FillResponse(
            BigDecimal quantity, BigDecimal averagePrice, Instant executedAt, String externalExecutionRef) {
        static FillResponse from(ManualFill value) {
            return new FillResponse(
                    value.quantity(), value.averagePrice(), value.executedAt(), value.externalExecutionRef());
        }
    }
}
