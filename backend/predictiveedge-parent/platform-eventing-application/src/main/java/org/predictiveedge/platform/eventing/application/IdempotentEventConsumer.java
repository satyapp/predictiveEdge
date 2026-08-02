package org.predictiveedge.platform.eventing.application;

import java.util.Objects;

/** Application entry point for idempotent, transactional event handling. */
public final class IdempotentEventConsumer {
    private final InboxTransaction inbox;

    public IdempotentEventConsumer(InboxTransaction inbox) {
        this.inbox = Objects.requireNonNull(inbox, "Inbox transaction is required");
    }

    public ProcessingResult consume(
            String consumerName, EventDelivery delivery, EventHandler handler) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("Consumer name is required");
        }
        Objects.requireNonNull(delivery, "Event delivery is required");
        Objects.requireNonNull(handler, "Event handler is required");
        return Objects.requireNonNull(
                inbox.executeOnce(consumerName, delivery, handler),
                "Inbox transaction returned no result");
    }
}
