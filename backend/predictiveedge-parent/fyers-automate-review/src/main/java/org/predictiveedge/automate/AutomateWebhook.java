package org.predictiveedge.automate;

import java.time.Instant;
import java.util.UUID;

/** A trader-registered FYERS Automate webhook. The URL itself is a secret and is stored encrypted. */
public record AutomateWebhook(
        UUID webhookId,
        UUID userId,
        String label,
        String maskedUrl,
        boolean enabled,
        Instant createdAt,
        Instant lastDeliveredAt) { }
