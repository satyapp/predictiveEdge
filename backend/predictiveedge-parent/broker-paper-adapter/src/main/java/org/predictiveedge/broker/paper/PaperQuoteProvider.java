package org.predictiveedge.broker.paper;

import java.math.BigDecimal;
import org.predictiveedge.broker.domain.Instrument;

@FunctionalInterface
public interface PaperQuoteProvider {
    BigDecimal marketPrice(Instrument instrument);
}
