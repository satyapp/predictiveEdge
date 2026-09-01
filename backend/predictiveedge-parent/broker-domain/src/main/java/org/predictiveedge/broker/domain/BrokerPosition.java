package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record BrokerPosition(
        Instrument instrument,
        String providerInstrumentId,
        String product,
        long quantity,
        long overnightQuantity,
        BigDecimal averagePrice,
        BigDecimal closePrice,
        BigDecimal lastPrice,
        BigDecimal value,
        BigDecimal pnl,
        BigDecimal m2m,
        BigDecimal unrealised,
        BigDecimal realised,
        long buyQuantity,
        long sellQuantity) {
    public BrokerPosition {
        Objects.requireNonNull(instrument, "Position instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank())
            throw new IllegalArgumentException("Provider instrument id is required");
        if (product == null || product.isBlank()) throw new IllegalArgumentException("Position product is required");
        Objects.requireNonNull(averagePrice, "Average price is required");
        Objects.requireNonNull(closePrice, "Close price is required");
        Objects.requireNonNull(lastPrice, "Last price is required");
        Objects.requireNonNull(value, "Position value is required");
        Objects.requireNonNull(pnl, "Position P&L is required");
        Objects.requireNonNull(m2m, "Position M2M is required");
        Objects.requireNonNull(unrealised, "Unrealised P&L is required");
        Objects.requireNonNull(realised, "Realised P&L is required");
        if (buyQuantity < 0 || sellQuantity < 0) throw new IllegalArgumentException("Trade quantities cannot be negative");
    }
}
