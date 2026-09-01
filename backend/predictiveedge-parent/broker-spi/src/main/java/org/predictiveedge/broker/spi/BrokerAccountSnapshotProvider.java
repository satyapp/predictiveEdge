package org.predictiveedge.broker.spi;

import org.predictiveedge.broker.domain.BrokerAccountSnapshot;

@FunctionalInterface
public interface BrokerAccountSnapshotProvider {
    BrokerAccountSnapshot accountSnapshot(BrokerContext context);
}
