package org.predictiveedge.broker.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** One provider-normalized price level. Position is one-based within its bid or ask side. */
public record MarketDepthLevel(int position, BigDecimal price, long quantity, int orderCount) {
    public MarketDepthLevel {
        if (position < 1 || position > 5) throw new IllegalArgumentException("Depth position must be between 1 and 5");
        Objects.requireNonNull(price, "Depth price is required");
        if (price.signum() < 0) throw new IllegalArgumentException("Depth price cannot be negative");
        if (quantity < 0) throw new IllegalArgumentException("Depth quantity cannot be negative");
        if (orderCount < 0 || orderCount > 0xffff) {
            throw new IllegalArgumentException("Depth order count must fit uint16");
        }
        if (price.signum() == 0 && (quantity != 0 || orderCount != 0)) {
            throw new IllegalArgumentException("An empty depth price cannot carry quantity or orders");
        }
    }

    public static List<MarketDepthLevel> emptyBook() {
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(position -> new MarketDepthLevel(position, BigDecimal.ZERO, 0, 0)).toList();
    }
}
