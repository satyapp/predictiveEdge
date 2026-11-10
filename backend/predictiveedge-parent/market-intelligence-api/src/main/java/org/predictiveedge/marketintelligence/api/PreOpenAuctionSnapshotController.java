package org.predictiveedge.marketintelligence.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-intelligence/v1/pre-open-snapshots")
public class PreOpenAuctionSnapshotController {
    private final PreOpenAuctionPublicationPort snapshots;

    public PreOpenAuctionSnapshotController(PreOpenAuctionPublicationPort snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping
    public Response publish(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody Request request) {
        Instant receivedAt = Instant.now();
        List<SnapshotResponse> stored = request.snapshots().stream()
                .map(value -> snapshots.publish(identity.user().id(), request.brokerAccountId(),
                        value.toObservation(), receivedAt))
                .map(SnapshotResponse::from)
                .toList();
        return new Response(receivedAt, stored);
    }

    public record Request(
            @NotBlank String brokerAccountId,
            @NotEmpty @Size(max = 100) List<@Valid SnapshotRequest> snapshots) { }

    public record SnapshotRequest(
            @NotBlank String venue,
            @NotBlank String symbol,
            @NotBlank String series,
            @NotNull LocalDate tradingDate,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal previousClose,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal auctionPrice,
            @PositiveOrZero long auctionVolume,
            @NotNull @PositiveOrZero BigDecimal auctionTurnover,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal bestBid,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal bestAsk,
            @NotNull Instant observedAt) {
        PreOpenAuctionObservation toObservation() {
            return new PreOpenAuctionObservation(venue, symbol, series, tradingDate, previousClose, auctionPrice,
                    auctionVolume, auctionTurnover, bestBid, bestAsk, observedAt);
        }
    }

    public record Response(Instant receivedAt, List<SnapshotResponse> snapshots) { }

    public record SnapshotResponse(
            String snapshotId, String venue, String symbol, LocalDate tradingDate,
            Instant observedAt, Instant receivedAt, String evidenceHash) {
        static SnapshotResponse from(PreOpenAuctionSnapshot value) {
            return new SnapshotResponse(value.snapshotId().toString(), value.observation().venue(),
                    value.observation().symbol(), value.observation().tradingDate(),
                    value.observation().observedAt(), value.receivedAt(), value.evidenceHash());
        }
    }
}
