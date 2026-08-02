package org.predictiveedge.platform.eventing.infrastructure;

import java.util.UUID;

public final class OutboxLeaseLostException extends IllegalStateException {
    public OutboxLeaseLostException(UUID outboxId) {
        super("Outbox lease is no longer owned for " + outboxId);
    }
}
