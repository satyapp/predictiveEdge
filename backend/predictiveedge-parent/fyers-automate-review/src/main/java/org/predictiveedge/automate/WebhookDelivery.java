package org.predictiveedge.automate;

import java.time.Instant;
import java.util.UUID;

/** Audit record of one POST to a FYERS Automate webhook. The HTTP status is all Automate returns. */
public record WebhookDelivery(
        UUID deliveryId,
        UUID reviewId,
        UUID webhookId,
        RecommendationStage stage,
        Instant sentAt,
        Integer httpStatus,
        boolean succeeded,
        String error) { }
