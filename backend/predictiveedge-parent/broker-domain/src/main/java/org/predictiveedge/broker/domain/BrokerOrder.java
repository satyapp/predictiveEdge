package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BrokerOrder(
        String brokerOrderId,
        UUID clientOrderId,
        Instrument instrument,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        BigDecimal requestedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        Instant createdAt) {

    public BrokerOrder {
        if (brokerOrderId == null || brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("Broker order id is required");
        }
        Objects.requireNonNull(clientOrderId, "Client order id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(side, "Order side is required");
        Objects.requireNonNull(type, "Order type is required");
        Objects.requireNonNull(status, "Order status is required");
        Objects.requireNonNull(requestedQuantity, "Requested quantity is required");
        Objects.requireNonNull(filledQuantity, "Filled quantity is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
    }
}
