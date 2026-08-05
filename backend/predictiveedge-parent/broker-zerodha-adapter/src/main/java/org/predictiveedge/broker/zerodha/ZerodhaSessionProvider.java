package org.predictiveedge.broker.zerodha;

import org.predictiveedge.broker.spi.BrokerContext;

@FunctionalInterface
public interface ZerodhaSessionProvider {
    ZerodhaSession sessionFor(BrokerContext context);

    /** Evicts the exact rejected credential when the provider reports an authentication failure. */
    default void authenticationFailed(BrokerContext context, ZerodhaSession rejectedSession) { }
}
