package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the review-only FYERS Automate loop. No endpoint here places a broker order. */
@RestController
@RequestMapping("/api/fyers-automate/v1")
@ConditionalOnProperty(prefix = "predictiveedge.fyers-automate", name = "enabled", havingValue = "true")
public class AutomateReviewController {
    private final AutomateReviewService service;

    public AutomateReviewController(AutomateReviewService service) { this.service = service; }

    // ---- Webhooks
    @GetMapping("/webhooks")
    public List<AutomateWebhook> webhooks(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return service.webhooks(user(identity));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<AutomateWebhook> register(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestBody WebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerWebhook(user(identity), request.label(), request.url()));
    }

    @PatchMapping("/webhooks/{webhookId}")
    public AutomateWebhook update(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID webhookId,
            @RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) throw new IllegalArgumentException("enabled is required");
        return service.setWebhookEnabled(user(identity), webhookId, enabled);
    }

    @DeleteMapping("/webhooks/{webhookId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID webhookId) {
        service.deleteWebhook(user(identity), webhookId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhooks/{webhookId}/test")
    public WebhookDelivery test(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID webhookId) {
        return service.testWebhook(user(identity), webhookId);
    }

    // ---- Reviews
    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestBody CreateReviewRequest request) {
        RecommendationReview review = service.createStageOne(user(identity), request.exchange(), request.symbol(),
                request.sourceRef(), request.recommendation().draft(), request.source(), request.webhookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.of(review));
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> history(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) String symbol, @RequestParam(defaultValue = "100") int limit) {
        return service.history(user(identity), symbol, limit).stream().map(ReviewResponse::of).toList();
    }

    @GetMapping("/reviews/{reviewId}")
    public ReviewResponse review(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId) {
        return ReviewResponse.of(service.review(user(identity), reviewId));
    }

    @PostMapping("/reviews/{reviewId}/deliveries")
    public WebhookDelivery deliver(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId,
            @RequestBody DeliveryRequest request) {
        if (request.stage() == null || request.webhookId() == null) throw new IllegalArgumentException("stage and webhookId are required");
        return service.deliver(user(identity), reviewId, request.stage(), request.webhookId());
    }

    @PostMapping("/reviews/{reviewId}/fia-review")
    public ReviewResponse fia(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId,
            @RequestBody DraftRequest request) {
        return ReviewResponse.of(service.recordFiaReview(user(identity), reviewId, request.draft()));
    }

    @PostMapping("/reviews/{reviewId}/final")
    public ReviewResponse buildFinal(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId) {
        return ReviewResponse.of(service.buildFinal(user(identity), reviewId));
    }

    @PostMapping("/reviews/{reviewId}/guardian-revisions")
    public ReviewResponse guardian(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId,
            @RequestBody(required = false) DraftRequest request) {
        return ReviewResponse.of(service.recordGuardianRevision(user(identity), reviewId, request == null ? null : request.draft()));
    }

    @PostMapping("/reviews/{reviewId}/outcome")
    public ReviewResponse outcome(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reviewId,
            @RequestBody OutcomeRequest r) {
        return ReviewResponse.of(service.recordOutcome(user(identity), reviewId, r.direction(), r.quantity(), r.entryPrice(),
                r.exitPrice(), r.enteredAt(), r.exitedAt(), r.charges(), r.followedStage(), r.monitoringCaseId(), r.notes()));
    }

    @GetMapping("/learning")
    public LearningResponse learning(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) String symbol) {
        LearningSummary s = service.learning(user(identity), symbol);
        return new LearningResponse(s.closedTrades(), s.winningTrades(), s.winRatePercent(), s.totalPnl(), s.byFollowedStage(),
                s.fiaAgreedWithStageOne(), s.fiaDisagreedWithStageOne(), s.stageOneDirectionCorrect(), s.fiaDirectionCorrect());
    }

    private static UUID user(AuthenticatedIdentity identity) { return identity.user().id(); }

    // ---- DTOs
    public record WebhookRequest(String label, String url) { }

    public record DraftRequest(TradeAction action, BigDecimal entryLow, BigDecimal entryHigh, BigDecimal stopLoss,
            BigDecimal targetOne, BigDecimal targetTwo, Integer quantity, Integer confidence, String rationale) {
        RecommendationDraft draft() {
            return new RecommendationDraft(action, entryLow, entryHigh, stopLoss, targetOne, targetTwo, quantity, confidence, rationale);
        }
    }

    public record CreateReviewRequest(String exchange, String symbol, String sourceRef, String source, UUID webhookId,
            DraftRequest recommendation) {
        public CreateReviewRequest {
            if (recommendation == null) throw new IllegalArgumentException("recommendation is required");
        }
    }

    public record DeliveryRequest(RecommendationStage stage, UUID webhookId) { }

    public record OutcomeRequest(TradeAction direction, BigDecimal quantity, BigDecimal entryPrice, BigDecimal exitPrice,
            Instant enteredAt, Instant exitedAt, BigDecimal charges, RecommendationStage followedStage, UUID monitoringCaseId,
            String notes) { }

    public record OutcomeResponse(TradeAction direction, BigDecimal quantity, BigDecimal entryPrice, BigDecimal exitPrice,
            Instant enteredAt, Instant exitedAt, BigDecimal charges, BigDecimal realizedPnl, BigDecimal returnPercent,
            RecommendationStage followedStage, UUID monitoringCaseId, String notes, Instant recordedAt) {
        static OutcomeResponse of(TradeOutcome o) {
            return o == null ? null : new OutcomeResponse(o.direction(), o.quantity(), o.entryPrice(), o.exitPrice(),
                    o.enteredAt(), o.exitedAt(), o.charges(), o.realizedPnl(), o.returnPercent(), o.followedStage(),
                    o.monitoringCaseId(), o.notes(), o.recordedAt());
        }
    }

    public record VersionResponse(UUID versionId, RecommendationStage stage, String source, Instant createdAt,
            TradeAction action, BigDecimal entryLow, BigDecimal entryHigh, BigDecimal stopLoss, BigDecimal targetOne,
            BigDecimal targetTwo, Integer quantity, Integer confidence, String rationale) {
        static VersionResponse of(RecommendationVersion v) {
            RecommendationDraft d = v.draft();
            return new VersionResponse(v.versionId(), v.stage(), v.source(), v.createdAt(), d.action(), d.entryLow(),
                    d.entryHigh(), d.stopLoss(), d.targetOne(), d.targetTwo(), d.quantity(), d.confidence(), d.rationale());
        }
    }

    public record ReviewResponse(UUID reviewId, String exchange, String symbol, String fyersSymbol, String sourceRef,
            ReviewStatus status, Instant createdAt, Instant updatedAt, List<VersionResponse> versions,
            List<WebhookDelivery> deliveries, OutcomeResponse outcome, boolean liveOrderPlaced) {
        static ReviewResponse of(RecommendationReview r) {
            return new ReviewResponse(r.reviewId(), r.exchange(), r.symbol(), r.fyersSymbol(), r.sourceRef(), r.status(),
                    r.createdAt(), r.updatedAt(), r.versions().stream()
                            .sorted(Comparator.comparing(RecommendationVersion::createdAt)).map(VersionResponse::of).toList(),
                    r.deliveries(), OutcomeResponse.of(r.outcome()), false);
        }
    }

    public record LearningResponse(int closedTrades, int winningTrades, BigDecimal winRatePercent, BigDecimal totalPnl,
            Map<RecommendationStage, LearningSummary.StageRecord> byFollowedStage, int fiaAgreedWithStageOne,
            int fiaDisagreedWithStageOne, int stageOneDirectionCorrect, int fiaDirectionCorrect) { }
}
