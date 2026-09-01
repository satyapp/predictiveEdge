package org.predictiveedge.broker.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only broker facts collected during a bounded, non-atomic observation window. */
public record BrokerAccountSnapshot(
        BrokerId brokerId,
        String accountId,
        Map<String, BrokerFundsSegment> funds,
        List<BrokerPosition> netPositions,
        List<BrokerPosition> dayPositions,
        List<BrokerHolding> holdings,
        Instant observedAt,
        Instant receivedAt) {
    public BrokerAccountSnapshot {
        Objects.requireNonNull(brokerId, "Broker id is required");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("Account id is required");
        funds = Map.copyOf(Objects.requireNonNull(funds, "Funds are required"));
        netPositions = List.copyOf(Objects.requireNonNull(netPositions, "Net positions are required"));
        dayPositions = List.copyOf(Objects.requireNonNull(dayPositions, "Day positions are required"));
        holdings = List.copyOf(Objects.requireNonNull(holdings, "Holdings are required"));
        Objects.requireNonNull(observedAt, "Observation start is required");
        Objects.requireNonNull(receivedAt, "Receipt time is required");
        if (receivedAt.isBefore(observedAt)) throw new IllegalArgumentException("Receipt cannot precede observation");
    }
}
