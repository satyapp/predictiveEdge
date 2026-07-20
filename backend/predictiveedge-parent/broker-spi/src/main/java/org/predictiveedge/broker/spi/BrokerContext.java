package org.predictiveedge.broker.spi;

import java.util.Objects;
import java.util.UUID;

public record BrokerContext(UUID userId, String brokerAccountId, String credentialReference) {
    public BrokerContext {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank()) {
            throw new IllegalArgumentException("Broker account id is required");
        }
    }

    public static BrokerContext withoutCredentials(UUID userId, String brokerAccountId) {
        return new BrokerContext(userId, brokerAccountId, null);
    }
}
