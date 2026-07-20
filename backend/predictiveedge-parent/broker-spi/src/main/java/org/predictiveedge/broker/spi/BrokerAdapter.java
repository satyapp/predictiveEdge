package org.predictiveedge.broker.spi;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.predictiveedge.broker.domain.BrokerAccount;
import org.predictiveedge.broker.domain.BrokerCapability;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.domain.BrokerOrder;
import org.predictiveedge.broker.domain.OrderRequest;

public interface BrokerAdapter {
    BrokerId id();
    String displayName();
    Set<BrokerCapability> capabilities();
    BrokerAccount account(BrokerContext context);
    BrokerOrder placeOrder(BrokerContext context, OrderRequest request);
    Optional<BrokerOrder> findOrder(BrokerContext context, UUID clientOrderId);
}
