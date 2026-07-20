package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record BrokerAccount(
        BrokerId brokerId,
        String accountId,
        String displayName,
        BigDecimal availableCash,
        Map<Instrument, BigDecimal> positions,
        Instant asOf) {

    public BrokerAccount {
        Objects.requireNonNull(brokerId, "Broker id is required");
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        Objects.requireNonNull(availableCash, "Available cash is required");
        positions = Map.copyOf(Objects.requireNonNull(positions, "Positions are required"));
        Objects.requireNonNull(asOf, "Snapshot time is required");
    }
}
