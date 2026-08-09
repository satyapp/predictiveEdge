package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.Optional;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.marketintelligence.domain.MarketSession;

/** Effective-dated market calendar boundary used to align incoming ticks. */
public interface MarketSessionPort {
    Optional<MarketSession> sessionFor(Instrument instrument, Instant exchangeTimestamp);
}
