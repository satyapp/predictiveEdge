package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record BrokerFundsSegment(
        String segment,
        boolean enabled,
        BigDecimal net,
        Map<String, BigDecimal> available,
        Map<String, BigDecimal> utilised) {
    public BrokerFundsSegment {
        if (segment == null || segment.isBlank()) throw new IllegalArgumentException("Funds segment is required");
        Objects.requireNonNull(net, "Net funds are required");
        available = Map.copyOf(Objects.requireNonNull(available, "Available funds are required"));
        utilised = Map.copyOf(Objects.requireNonNull(utilised, "Utilised funds are required"));
    }
}
