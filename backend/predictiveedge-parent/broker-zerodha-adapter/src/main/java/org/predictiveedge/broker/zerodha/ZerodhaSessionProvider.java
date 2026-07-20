package org.predictiveedge.broker.zerodha;

import org.predictiveedge.broker.spi.BrokerContext;

@FunctionalInterface
public interface ZerodhaSessionProvider {
    ZerodhaSession sessionFor(BrokerContext context);
}
