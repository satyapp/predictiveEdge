package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.automate.AutomateReviewFailure.Code;
import org.predictiveedge.automate.AutomateReviewStore.StoredWebhook;

/**
 * Use cases for the review loop:
 * PredictiveEdge stage one → FYERS Automate → trader reviews with FIA → FIA review recorded here →
 * AI + FIA final → back to FYERS Automate → trader confirms any order in FYERS → outcome recorded for learning.
 *
 * <p>No method here places, modifies or cancels a broker order.
 */
public final class AutomateReviewService {
    private static final int LEARNING_WINDOW = 50;

    private final AutomateReviewStore store;
    private final CredentialCipher cipher;
    private final AutomateWebhookSender sender;
    private final ReconciliationEngine reconciliation;
    private final GuardianAdvisor guardian;
    private final AutomatePayloads payloads;
    private final WebhookUrlPolicy urlPolicy;
    private final Duration minimumDeliveryInterval;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public AutomateReviewService(AutomateReviewStore store, CredentialCipher cipher, AutomateWebhookSender sender,
            ReconciliationEngine reconciliation, GuardianAdvisor guardian, AutomatePayloads payloads,
            WebhookUrlPolicy urlPolicy, Duration minimumDeliveryInterval, Clock clock, Supplier<UUID> ids) {
        this.store = Objects.requireNonNull(store);
        this.cipher = Objects.requireNonNull(cipher);
        this.sender = Objects.requireNonNull(sender);
        this.reconciliation = Objects.requireNonNull(reconciliation);
        this.guardian = Objects.requireNonNull(guardian);
        this.payloads = Objects.requireNonNull(payloads);
        this.urlPolicy = Objects.requireNonNull(urlPolicy);
        this.minimumDeliveryInterval = Objects.requireNonNull(minimumDeliveryInterval);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    // ---- Webhooks -------------------------------------------------------------------------------------------

    public AutomateWebhook registerWebhook(UUID userId, String label, String url) {
        URI uri = urlPolicy.validate(url);
        String name = label == null || label.isBlank() ? "FYERS Automate" : label.strip();
        if (name.length() > 120) throw new IllegalArgumentException("Label must be at most 120 characters");
        String fingerprint = WebhookUrlPolicy.fingerprint(uri);
        if (store.webhooks(userId).stream().anyMatch(w -> w.urlFingerprint().equals(fingerprint))) {
            throw new AutomateReviewFailure(Code.INVALID_STATE, "This webhook URL is already registered");
        }
        StoredWebhook webhook = new StoredWebhook(ids.get(), userId, name, cipher.encrypt(uri.toString()), fingerprint,
                WebhookUrlPolicy.mask(uri), true, clock.instant(), null);
        store.saveWebhook(webhook);
        return webhook.view();
    }

    public List<AutomateWebhook> webhooks(UUID userId) {
        return store.webhooks(userId).stream().map(StoredWebhook::view).toList();
    }

    public AutomateWebhook setWebhookEnabled(UUID userId, UUID webhookId, boolean enabled) {
        ownedWebhook(userId, webhookId);
        store.setWebhookEnabled(userId, webhookId, enabled);
        return ownedWebhook(userId, webhookId).view();
    }

    public void deleteWebhook(UUID userId, UUID webhookId) {
        ownedWebhook(userId, webhookId);
        store.deleteWebhook(userId, webhookId);
    }

    public WebhookDelivery testWebhook(UUID userId, UUID webhookId) {
        StoredWebhook webhook = ownedWebhook(userId, webhookId);
        Instant now = clock.instant();
        return post(webhook, null, null, payloads.test(now), now);
    }

    // ---- Review loop ----------------------------------------------------------------------------------------

    /** Stores PredictiveEdge's stage-one recommendation and, when a webhook is given, sends it to Automate. */
    public RecommendationReview createStageOne(UUID userId, String exchange, String symbol, String sourceRef,
            RecommendationDraft draft, String source, UUID webhookId) {
        Objects.requireNonNull(draft, "Recommendation is required");
        Instant now = clock.instant();
        RecommendationVersion version = new RecommendationVersion(ids.get(), RecommendationStage.PREDICTIVEEDGE_STAGE_ONE,
                draft, source == null || source.isBlank() ? "PredictiveEdge" : source.strip(), now);
        RecommendationReview review = new RecommendationReview(ids.get(), userId, exchange, symbol, sourceRef,
                ReviewStatus.STAGE_ONE_CREATED, now, now, List.of(version), List.of(), null);
        if (webhookId != null) ownedWebhook(userId, webhookId);
        store.createReview(review);
        if (webhookId != null) deliver(userId, review.reviewId(), RecommendationStage.PREDICTIVEEDGE_STAGE_ONE, webhookId);
        return review(userId, review.reviewId());
    }

    /** Sends the latest version at a stage to a FYERS Automate webhook. Review-only: never an order. */
    public WebhookDelivery deliver(UUID userId, UUID reviewId, RecommendationStage stage, UUID webhookId) {
        RecommendationReview review = review(userId, reviewId);
        if (review.status() == ReviewStatus.CLOSED) throw new AutomateReviewFailure(Code.INVALID_STATE, "This trade is closed");
        if (stage == RecommendationStage.FIA_REVIEW) {
            throw new AutomateReviewFailure(Code.INVALID_STATE, "The FIA review comes from FYERS and is not sent back to it");
        }
        RecommendationVersion version = review.latest(stage).orElseThrow(() -> new AutomateReviewFailure(
                Code.INVALID_STATE, "There is no " + label(stage) + " to send yet"));
        StoredWebhook webhook = ownedWebhook(userId, webhookId);
        if (!webhook.enabled()) throw new AutomateReviewFailure(Code.INVALID_STATE, "This webhook is turned off");
        Instant now = clock.instant();
        if (webhook.lastDeliveredAt() != null) {
            Duration since = Duration.between(webhook.lastDeliveredAt(), now);
            if (since.compareTo(minimumDeliveryInterval) < 0) {
                long wait = minimumDeliveryInterval.minus(since).toSeconds() + 1;
                throw new AutomateReviewFailure(Code.RATE_LIMITED, "FYERS Automate accepts one call a minute per webhook. Try again in "
                        + wait + " seconds.");
            }
        }
        WebhookDelivery delivery = post(webhook, review, stage, payloads.recommendation(review, version, now), now);
        if (delivery.succeeded()) {
            ReviewStatus next = switch (stage) {
                case PREDICTIVEEDGE_STAGE_ONE -> review.status() == ReviewStatus.STAGE_ONE_CREATED
                        || review.status() == ReviewStatus.STAGE_ONE_SENT ? ReviewStatus.AWAITING_FIA_REVIEW : review.status();
                case FINAL_AI_FIA -> ReviewStatus.FINAL_SENT;
                default -> review.status();
            };
            if (next != review.status()) store.updateStatus(reviewId, next, now);
        }
        return delivery;
    }

    /** Records FIA's reasoning and correction, as entered by the trader after reviewing in FYERS. */
    public RecommendationReview recordFiaReview(UUID userId, UUID reviewId, RecommendationDraft draft) {
        RecommendationReview review = review(userId, reviewId);
        if (review.status() == ReviewStatus.CLOSED) throw new AutomateReviewFailure(Code.INVALID_STATE, "This trade is closed");
        if (draft.rationale().isBlank()) throw new IllegalArgumentException("Paste FIA's reasoning so the AI engine can weigh it");
        Instant now = clock.instant();
        store.addVersion(reviewId, new RecommendationVersion(ids.get(), RecommendationStage.FIA_REVIEW, draft,
                "FIA (entered by trader)", now));
        store.updateStatus(reviewId, ReviewStatus.FIA_REVIEW_RECORDED, now);
        return review(userId, reviewId);
    }

    /** Runs the AI engine over stage one, the latest FIA review, and the realized track record. */
    public RecommendationReview buildFinal(UUID userId, UUID reviewId) {
        RecommendationReview review = review(userId, reviewId);
        if (review.status() == ReviewStatus.CLOSED) throw new AutomateReviewFailure(Code.INVALID_STATE, "This trade is closed");
        RecommendationVersion stageOne = review.latest(RecommendationStage.PREDICTIVEEDGE_STAGE_ONE).orElseThrow();
        RecommendationVersion fia = review.latest(RecommendationStage.FIA_REVIEW).orElseThrow(() ->
                new AutomateReviewFailure(Code.INVALID_STATE, "Record FIA's review before building the final recommendation"));
        List<RecommendationReview> sameStock = store.closedReviews(userId, review.symbol(), 10);
        LearningSummary learning = LearningSummary.from(store.closedReviews(userId, null, LEARNING_WINDOW));
        ReconciliationEngine.Result result = reconciliation.reconcile(new ReconciliationEngine.Input(
                review.exchange(), review.symbol(), stageOne, fia, learning, sameStock));
        Instant now = clock.instant();
        store.addVersion(reviewId, new RecommendationVersion(ids.get(), RecommendationStage.FINAL_AI_FIA, result.draft(),
                "PredictiveEdge AI + FIA (" + result.engine() + ")", now));
        store.updateStatus(reviewId, ReviewStatus.FINAL_READY, now);
        return review(userId, reviewId);
    }

    /**
     * Trade Guardian placeholder. Records an advisory revision, either supplied by the trader or proposed by
     * the configured {@link GuardianAdvisor}. It never changes a broker order; resend it to Automate to review.
     */
    public RecommendationReview recordGuardianRevision(UUID userId, UUID reviewId, RecommendationDraft supplied) {
        RecommendationReview review = review(userId, reviewId);
        if (review.status() == ReviewStatus.CLOSED) throw new AutomateReviewFailure(Code.INVALID_STATE, "This trade is closed");
        if (review.latest(RecommendationStage.FINAL_AI_FIA).isEmpty()) {
            throw new AutomateReviewFailure(Code.INVALID_STATE, "Trade Guardian revises the final recommendation; build it first");
        }
        Optional<RecommendationDraft> draft = supplied != null ? Optional.of(supplied) : guardian.reassess(review);
        if (draft.isEmpty()) return review;
        store.addVersion(reviewId, new RecommendationVersion(ids.get(), RecommendationStage.GUARDIAN_REVISION, draft.get(),
                supplied != null ? "Trade Guardian (entered by trader)" : "Trade Guardian", clock.instant()));
        return review(userId, reviewId);
    }

    /** Records the trade actually taken in FYERS and closes the review; it then feeds future reconciliation. */
    public RecommendationReview recordOutcome(UUID userId, UUID reviewId, TradeAction direction, BigDecimal quantity,
            BigDecimal entryPrice, BigDecimal exitPrice, Instant enteredAt, Instant exitedAt, BigDecimal charges,
            RecommendationStage followedStage, UUID monitoringCaseId, String notes) {
        RecommendationReview review = review(userId, reviewId);
        if (review.outcome() != null) throw new AutomateReviewFailure(Code.INVALID_STATE, "This trade's outcome is already recorded");
        Instant now = clock.instant();
        if (exitedAt != null && exitedAt.isAfter(now.plusSeconds(60))) throw new IllegalArgumentException("Exit time cannot be in the future");
        RecommendationStage followed = followedStage == null
                ? review.current().map(RecommendationVersion::stage).orElse(RecommendationStage.PREDICTIVEEDGE_STAGE_ONE)
                : followedStage;
        if (review.latest(followed).isEmpty()) throw new IllegalArgumentException("This review has no " + label(followed));
        store.saveOutcome(reviewId, new TradeOutcome(direction, quantity, entryPrice, exitPrice, enteredAt, exitedAt,
                charges, followed, monitoringCaseId, notes, now));
        store.updateStatus(reviewId, ReviewStatus.CLOSED, now);
        return review(userId, reviewId);
    }

    public RecommendationReview review(UUID userId, UUID reviewId) {
        return store.review(userId, reviewId).orElseThrow(() ->
                new AutomateReviewFailure(Code.NOT_FOUND, "Recommendation review was not found"));
    }

    public List<RecommendationReview> history(UUID userId, String symbol, int limit) {
        String filter = symbol == null || symbol.isBlank() ? null : RecommendationReview.normalizeSymbol(symbol);
        return store.reviews(userId, filter, Math.max(1, Math.min(limit, 500)));
    }

    public LearningSummary learning(UUID userId, String symbol) {
        String filter = symbol == null || symbol.isBlank() ? null : RecommendationReview.normalizeSymbol(symbol);
        return LearningSummary.from(store.closedReviews(userId, filter, 500));
    }

    // ---- Internals ------------------------------------------------------------------------------------------

    private WebhookDelivery post(StoredWebhook webhook, RecommendationReview review, RecommendationStage stage,
            String payload, Instant now) {
        AutomateWebhookSender.Result result;
        try {
            result = sender.post(URI.create(cipher.decrypt(webhook.encryptedUrl())), payload);
        } catch (RuntimeException failure) {
            result = new AutomateWebhookSender.Result(null, failure.getMessage() == null ? "Delivery failed" : failure.getMessage());
        }
        String error = result.succeeded() ? null : result.error() != null ? result.error()
                : "FYERS Automate returned HTTP " + result.httpStatus();
        WebhookDelivery delivery = new WebhookDelivery(ids.get(), review == null ? null : review.reviewId(),
                webhook.webhookId(), stage, now, result.httpStatus(), result.succeeded(), error);
        store.addDelivery(delivery, payload);
        store.markWebhookDelivered(webhook.webhookId(), now);
        return delivery;
    }

    private StoredWebhook ownedWebhook(UUID userId, UUID webhookId) {
        return store.webhook(userId, webhookId).orElseThrow(() ->
                new AutomateReviewFailure(Code.NOT_FOUND, "Webhook was not found"));
    }

    private static String label(RecommendationStage stage) {
        return switch (stage) {
            case PREDICTIVEEDGE_STAGE_ONE -> "stage-one recommendation";
            case FIA_REVIEW -> "FIA review";
            case FINAL_AI_FIA -> "final recommendation";
            case GUARDIAN_REVISION -> "Trade Guardian revision";
        };
    }
}
