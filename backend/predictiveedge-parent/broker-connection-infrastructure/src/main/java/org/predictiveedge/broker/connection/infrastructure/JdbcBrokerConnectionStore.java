package org.predictiveedge.broker.connection.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.predictiveedge.broker.connection.BrokerConnectionStore;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcBrokerConnectionStore implements BrokerConnectionStore {
    private final JdbcTemplate jdbc;

    public JdbcBrokerConnectionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void createState(String stateHash, UUID userId, String ownerSessionHash, Instant expiresAt) {
        jdbc.update("insert into broker_connection_states (state_hash,user_id,broker_id,owner_session_hash,expires_at) values (?,?,?,?,?)",
                stateHash, userId, "zerodha", ownerSessionHash, Timestamp.from(expiresAt));
    }

    @Override
    public Optional<PendingConnection> consumeState(String stateHash, Instant now) {
        return jdbc.query("""
                update broker_connection_states set consumed_at=?
                where state_hash=? and consumed_at is null and expires_at>?
                returning user_id,owner_session_hash
                """, statement -> {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setString(2, stateHash);
                    statement.setTimestamp(3, Timestamp.from(now));
                }, result -> result.next() ? Optional.of(new PendingConnection(
                        result.getObject(1, UUID.class), result.getString(2))) : Optional.empty());
    }

    @Override
    public void saveZerodhaConnection(UUID userId, String externalAccountId, String encryptedAccessToken,
            String ownerSessionHash, Instant connectedAt, Instant leaseExpiresAt) {
        jdbc.update("""
                insert into broker_connections
                  (user_id,broker_id,external_account_id,encrypted_access_token,owner_session_hash,
                   connected_at,lease_expires_at,updated_at,revocation_started_at)
                values (?,?,?,?,?,?,?,?,null)
                on conflict (user_id,broker_id) do update set
                  external_account_id=excluded.external_account_id,
                  encrypted_access_token=excluded.encrypted_access_token,
                  owner_session_hash=excluded.owner_session_hash,
                  connected_at=excluded.connected_at,
                  lease_expires_at=excluded.lease_expires_at,
                  revocation_started_at=null,
                  updated_at=excluded.updated_at
                """, userId, "zerodha", externalAccountId, encryptedAccessToken, ownerSessionHash,
                Timestamp.from(connectedAt), Timestamp.from(leaseExpiresAt), Timestamp.from(connectedAt));
    }

    @Override
    public Optional<StoredBrokerConnection> findZerodhaConnection(UUID userId) {
        return jdbc.query("""
                select external_account_id,encrypted_access_token,owner_session_hash,connected_at,
                       lease_expires_at,revocation_started_at
                from broker_connections where user_id=? and broker_id='zerodha'
                """, statement -> statement.setObject(1, userId), result -> result.next()
                ? Optional.of(new StoredBrokerConnection(result.getString(1), result.getString(2), result.getString(3),
                        result.getTimestamp(4).toInstant(), result.getTimestamp(5).toInstant(),
                        result.getTimestamp(6) == null ? null : result.getTimestamp(6).toInstant()))
                : Optional.empty());
    }

    @Override
    public boolean renewZerodhaLease(UUID userId, String ownerSessionHash, Instant now, Instant leaseExpiresAt) {
        return jdbc.update("""
                update broker_connections set lease_expires_at=?,updated_at=?
                where user_id=? and broker_id='zerodha' and owner_session_hash=?
                  and revocation_started_at is null and lease_expires_at>?
                """, Timestamp.from(leaseExpiresAt), Timestamp.from(now), userId, ownerSessionHash,
                Timestamp.from(now)) == 1;
    }

    @Override
    public void shortenZerodhaLease(UUID userId, String ownerSessionHash, Instant now, Instant leaseExpiresAt) {
        jdbc.update("""
                update broker_connections set lease_expires_at=least(lease_expires_at,?),updated_at=?
                where user_id=? and broker_id='zerodha' and owner_session_hash=? and revocation_started_at is null
                """, Timestamp.from(leaseExpiresAt), Timestamp.from(now), userId, ownerSessionHash);
    }

    @Override
    public Optional<ClaimedBrokerConnection> claimZerodhaConnectionForRevocation(UUID userId, Instant now) {
        return jdbc.query("""
                update broker_connections set revocation_started_at=?,updated_at=?
                where user_id=? and broker_id='zerodha' and revocation_started_at is null
                returning user_id,encrypted_access_token
                """, statement -> {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setTimestamp(2, Timestamp.from(now));
                    statement.setObject(3, userId);
                }, result -> result.next() ? Optional.of(new ClaimedBrokerConnection(
                        result.getObject(1, UUID.class), result.getString(2))) : Optional.empty());
    }

    @Override
    public List<ClaimedBrokerConnection> claimExpiredZerodhaConnections(Instant now, int limit) {
        return jdbc.query("""
                with expired as (
                  select user_id from broker_connections
                  where broker_id='zerodha' and revocation_started_at is null and lease_expires_at<=?
                  order by lease_expires_at for update skip locked limit ?
                )
                update broker_connections connection
                set revocation_started_at=?,updated_at=?
                from expired where connection.user_id=expired.user_id and connection.broker_id='zerodha'
                returning connection.user_id,connection.encrypted_access_token
                """, statement -> {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setInt(2, limit);
                    statement.setTimestamp(3, Timestamp.from(now));
                    statement.setTimestamp(4, Timestamp.from(now));
                }, (result, row) -> new ClaimedBrokerConnection(
                        result.getObject(1, UUID.class), result.getString(2)));
    }

    @Override
    public void completeZerodhaRevocation(ClaimedBrokerConnection connection) {
        jdbc.update("""
                delete from broker_connections where user_id=? and broker_id='zerodha'
                  and encrypted_access_token=? and revocation_started_at is not null
                """, connection.userId(), connection.encryptedAccessToken());
    }

    @Override
    public void releaseZerodhaRevocation(ClaimedBrokerConnection connection, Instant now) {
        jdbc.update("""
                update broker_connections set revocation_started_at=null,updated_at=?
                where user_id=? and broker_id='zerodha' and encrypted_access_token=?
                  and revocation_started_at is not null
                """, Timestamp.from(now), connection.userId(), connection.encryptedAccessToken());
    }

    @Override
    public void deleteZerodhaConnection(UUID userId) {
        jdbc.update("delete from broker_connections where user_id=? and broker_id='zerodha'", userId);
    }
}
