package org.predictiveedge.fyers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcFyersConnectionStore implements FyersConnectionStore {
    private final JdbcTemplate jdbc;
    JdbcFyersConnectionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void createState(String hash, UUID userId, String sessionHash, Instant expiresAt) {
        jdbc.update("insert into broker_connection_states (state_hash,user_id,broker_id,owner_session_hash,expires_at) values (?,?,?,?,?)",
                hash, userId, "fyers", sessionHash, Timestamp.from(expiresAt));
    }
    public Optional<Pending> consumeState(String hash, Instant now) {
        return jdbc.query("""
                update broker_connection_states set consumed_at=?
                where state_hash=? and broker_id='fyers' and consumed_at is null and expires_at>?
                returning user_id,owner_session_hash
                """, ps -> { ps.setTimestamp(1, Timestamp.from(now)); ps.setString(2, hash);
                    ps.setTimestamp(3, Timestamp.from(now)); }, rs -> rs.next()
                        ? Optional.of(new Pending(rs.getObject(1, UUID.class), rs.getString(2))) : Optional.empty());
    }
    public void save(UUID userId, String accountId, String encryptedToken, String sessionHash,
            Instant connectedAt, Instant expiresAt) {
        jdbc.update("""
                insert into broker_connections(user_id,broker_id,external_account_id,encrypted_access_token,
                  owner_session_hash,connected_at,lease_expires_at,updated_at,revocation_started_at)
                values (?,'fyers',?,?,?,?,?,?,null)
                on conflict(user_id,broker_id) do update set external_account_id=excluded.external_account_id,
                  encrypted_access_token=excluded.encrypted_access_token,owner_session_hash=excluded.owner_session_hash,
                  connected_at=excluded.connected_at,lease_expires_at=excluded.lease_expires_at,
                  updated_at=excluded.updated_at,revocation_started_at=null
                """, userId, accountId, encryptedToken, sessionHash, Timestamp.from(connectedAt),
                Timestamp.from(expiresAt), Timestamp.from(connectedAt));
    }
    public Optional<Stored> find(UUID userId) {
        return jdbc.query("""
                select external_account_id,encrypted_access_token,connected_at,lease_expires_at
                from broker_connections where user_id=? and broker_id='fyers'
                """, ps -> ps.setObject(1, userId), rs -> rs.next() ? Optional.of(new Stored(
                        rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant())) : Optional.empty());
    }
    public void delete(UUID userId) {
        jdbc.update("delete from broker_connections where user_id=? and broker_id='fyers'", userId);
    }
}
