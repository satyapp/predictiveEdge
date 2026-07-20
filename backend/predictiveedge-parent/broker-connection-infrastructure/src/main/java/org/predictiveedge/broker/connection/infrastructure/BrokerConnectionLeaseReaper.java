package org.predictiveedge.broker.connection.infrastructure;

import org.predictiveedge.broker.connection.BrokerConnectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class BrokerConnectionLeaseReaper {
    private final BrokerConnectionService connections;

    public BrokerConnectionLeaseReaper(BrokerConnectionService connections) {
        this.connections = connections;
    }

    @Scheduled(fixedDelayString = "${predictiveedge.broker.lease-sweep-milliseconds:15000}")
    public void revokeExpiredBrowserLeases() {
        connections.revokeExpiredLeases();
    }
}
