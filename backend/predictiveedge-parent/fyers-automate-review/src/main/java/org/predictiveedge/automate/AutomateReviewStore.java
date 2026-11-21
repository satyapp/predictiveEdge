package org.predictiveedge.automate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for webhooks, reviews, versions, deliveries and outcomes. */
public interface AutomateReviewStore {
    void saveWebhook(StoredWebhook webhook);
    List<StoredWebhook> webhooks(UUID userId);
    Optional<StoredWebhook> webhook(UUID userId, UUID webhookId);
    void setWebhookEnabled(UUID userId, UUID webhookId, boolean enabled);
    void markWebhookDelivered(UUID webhookId, Instant deliveredAt);
    void deleteWebhook(UUID userId, UUID webhookId);

    void createReview(RecommendationReview review);
    void updateStatus(UUID reviewId, ReviewStatus status, Instant updatedAt);
    void addVersion(UUID reviewId, RecommendationVersion version);
    void addDelivery(WebhookDelivery delivery, String payloadJson);
    void saveOutcome(UUID reviewId, TradeOutcome outcome);
    Optional<RecommendationReview> review(UUID userId, UUID reviewId);
    List<RecommendationReview> reviews(UUID userId, String symbol, int limit);
    /** Closed reviews with an outcome, newest first, used as learning context. */
    List<RecommendationReview> closedReviews(UUID userId, String symbol, int limit);

    record StoredWebhook(UUID webhookId, UUID userId, String label, String encryptedUrl, String urlFingerprint,
            String maskedUrl, boolean enabled, Instant createdAt, Instant lastDeliveredAt) {
        AutomateWebhook view() {
            return new AutomateWebhook(webhookId, userId, label, maskedUrl, enabled, createdAt, lastDeliveredAt);
        }
    }
}
