package org.predictiveedge.broker.paper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.predictiveedge.broker.domain.Instrument;

public final class PaperQuoteBook implements PaperQuoteProvider {
    private final Map<Instrument, BigDecimal> prices = new ConcurrentHashMap<>();

    public void update(Instrument instrument, BigDecimal price) {
        if (instrument == null || price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Instrument and positive price are required");
        }
        prices.put(instrument, price);
    }

    @Override
    public BigDecimal marketPrice(Instrument instrument) {
        return prices.get(instrument);
    }
}
