package org.predictiveedge.broker.connection;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface BrokerConnectionStore {
    void createState(String stateHash, UUID userId, String ownerSessionHash, Instant expiresAt);
    Optional<PendingConnection> consumeState(String stateHash, Instant now);
    void saveZerodhaConnection(UUID userId, String externalAccountId, String encryptedAccessToken,
            String ownerSessionHash, Instant connectedAt, Instant leaseExpiresAt);
    Optional<StoredBrokerConnection> findZerodhaConnection(UUID userId);
    boolean renewZerodhaLease(UUID userId, String ownerSessionHash, Instant now, Instant leaseExpiresAt);
    void shortenZerodhaLease(UUID userId, String ownerSessionHash, Instant now, Instant leaseExpiresAt);
    Optional<ClaimedBrokerConnection> claimZerodhaConnectionForRevocation(UUID userId, Instant now);
    List<ClaimedBrokerConnection> claimExpiredZerodhaConnections(Instant now, int limit);
    void completeZerodhaRevocation(ClaimedBrokerConnection connection);
    void releaseZerodhaRevocation(ClaimedBrokerConnection connection, Instant now);
    void deleteZerodhaConnection(UUID userId);

    record PendingConnection(UUID userId, String ownerSessionHash) {}
    record StoredBrokerConnection(String externalAccountId, String encryptedAccessToken, String ownerSessionHash,
            Instant connectedAt, Instant leaseExpiresAt, Instant revocationStartedAt) {}
    record ClaimedBrokerConnection(UUID userId, String encryptedAccessToken) {}
}
