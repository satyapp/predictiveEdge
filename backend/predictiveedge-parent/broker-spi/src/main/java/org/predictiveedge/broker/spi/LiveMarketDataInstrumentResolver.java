package org.predictiveedge.broker.spi;

import java.util.List;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;

/** Resolves stable platform instruments to a broker's current streaming identifiers. */
public interface LiveMarketDataInstrumentResolver {
    List<LiveMarketDataInstrument> resolve(BrokerContext context, List<Instrument> instruments);
}
