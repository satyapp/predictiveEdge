package org.predictiveedge.fyers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface FyersConnectionStore {
    void createState(String hash, UUID userId, String sessionHash, Instant expiresAt);
    Optional<Pending> consumeState(String hash, Instant now);
    void save(UUID userId, String accountId, String encryptedToken, String sessionHash,
            Instant connectedAt, Instant expiresAt);
    Optional<Stored> find(UUID userId);
    void delete(UUID userId);

    record Pending(UUID userId, String sessionHash) { }
    record Stored(String accountId, String encryptedToken, Instant connectedAt, Instant expiresAt) { }
}
