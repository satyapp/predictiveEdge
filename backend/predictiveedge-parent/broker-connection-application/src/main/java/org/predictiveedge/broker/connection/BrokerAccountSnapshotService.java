package org.predictiveedge.broker.connection;

import java.util.Objects;
import org.predictiveedge.broker.spi.BrokerAccountSnapshotProvider;
import org.predictiveedge.broker.spi.BrokerContext;

/** Captures and immutably records one read-only broker account observation. */
public final class BrokerAccountSnapshotService {
    private final BrokerAccountSnapshotProvider provider;
    private final BrokerAccountEvidencePort evidence;

    public BrokerAccountSnapshotService(
            BrokerAccountSnapshotProvider provider, BrokerAccountEvidencePort evidence) {
        this.provider = Objects.requireNonNull(provider, "Account snapshot provider is required");
        this.evidence = Objects.requireNonNull(evidence, "Account evidence port is required");
    }

    public BrokerAccountEvidence capture(BrokerContext context) {
        Objects.requireNonNull(context, "Broker context is required");
        var snapshot = provider.accountSnapshot(context);
        if (!snapshot.accountId().equals(context.brokerAccountId()))
            throw new IllegalStateException("Broker snapshot account does not match the requested account");
        return evidence.publish(context.userId(), snapshot);
    }
}
