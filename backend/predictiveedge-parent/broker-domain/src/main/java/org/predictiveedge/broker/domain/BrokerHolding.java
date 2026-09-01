package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record BrokerHolding(
        Instrument instrument,
        String providerInstrumentId,
        String isin,
        String product,
        long quantity,
        long t1Quantity,
        long authorisedQuantity,
        long collateralQuantity,
        BigDecimal averagePrice,
        BigDecimal lastPrice,
        BigDecimal closePrice,
        BigDecimal pnl,
        BigDecimal dayChange,
        BigDecimal dayChangePercentage) {
    public BrokerHolding {
        Objects.requireNonNull(instrument, "Holding instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank())
            throw new IllegalArgumentException("Provider instrument id is required");
        if (isin == null || isin.isBlank()) throw new IllegalArgumentException("Holding ISIN is required");
        if (product == null || product.isBlank()) throw new IllegalArgumentException("Holding product is required");
        if (quantity < 0 || t1Quantity < 0 || authorisedQuantity < 0 || collateralQuantity < 0)
            throw new IllegalArgumentException("Holding quantities cannot be negative");
        Objects.requireNonNull(averagePrice, "Average price is required");
        Objects.requireNonNull(lastPrice, "Last price is required");
        Objects.requireNonNull(closePrice, "Close price is required");
        Objects.requireNonNull(pnl, "Holding P&L is required");
        Objects.requireNonNull(dayChange, "Day change is required");
        Objects.requireNonNull(dayChangePercentage, "Day change percentage is required");
    }
}
