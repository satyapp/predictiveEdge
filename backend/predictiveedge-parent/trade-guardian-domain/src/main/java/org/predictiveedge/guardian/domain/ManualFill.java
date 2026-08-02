package org.predictiveedge.guardian.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Actual execution facts entered by the trader, never proposed order values. */
public record ManualFill(
        BigDecimal quantity,
        BigDecimal averagePrice,
        Instant executedAt,
        String externalExecutionRef) {

    public ManualFill {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (averagePrice == null || averagePrice.signum() <= 0) {
            throw new IllegalArgumentException("Average fill price must be positive");
        }
        if (executedAt == null) {
            throw new IllegalArgumentException("Execution time is required");
        }
        if (externalExecutionRef != null) {
            externalExecutionRef = externalExecutionRef.trim();
            if (externalExecutionRef.isEmpty()) {
                throw new IllegalArgumentException("External execution reference cannot be blank");
            }
        }
        quantity = quantity.stripTrailingZeros();
        averagePrice = averagePrice.stripTrailingZeros();
    }
}
