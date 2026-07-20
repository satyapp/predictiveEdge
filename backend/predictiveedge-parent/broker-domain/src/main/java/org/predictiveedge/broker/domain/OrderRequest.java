package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OrderRequest(
        UUID clientOrderId,
        Instrument instrument,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice) {

    public OrderRequest {
        Objects.requireNonNull(clientOrderId, "Client order id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(side, "Order side is required");
        Objects.requireNonNull(type, "Order type is required");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("A positive limit price is required for limit orders");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("Market orders cannot have a limit price");
        }
    }
}
