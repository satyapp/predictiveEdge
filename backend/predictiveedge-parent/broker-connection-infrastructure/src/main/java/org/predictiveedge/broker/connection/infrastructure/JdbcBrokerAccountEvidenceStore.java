package org.predictiveedge.broker.connection.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.connection.BrokerAccountEvidence;
import org.predictiveedge.broker.connection.BrokerAccountEvidencePort;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;
import org.predictiveedge.broker.domain.BrokerFundsSegment;
import org.predictiveedge.broker.domain.BrokerHolding;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.domain.BrokerPosition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class JdbcBrokerAccountEvidenceStore implements BrokerAccountEvidencePort {
    private static final TypeReference<Map<String, BrokerFundsSegment>> FUNDS = new TypeReference<>() { };
    private static final TypeReference<List<BrokerPosition>> POSITIONS = new TypeReference<>() { };
    private static final TypeReference<List<BrokerHolding>> HOLDINGS = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcBrokerAccountEvidenceStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    @Transactional
    public BrokerAccountEvidence publish(UUID userId, BrokerAccountSnapshot snapshot) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(snapshot, "Broker account snapshot is required");
        String funds = write(snapshot.funds());
        String netPositions = write(snapshot.netPositions());
        String dayPositions = write(snapshot.dayPositions());
        String positions = "{\"net\":" + netPositions + ",\"day\":" + dayPositions + "}";
        String holdings = write(snapshot.holdings());
        String hash = hash(userId, snapshot, funds, positions, holdings);
        UUID snapshotId = UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into broker_evidence.account_snapshot (
                  snapshot_id,user_id,broker_account_id,broker,observed_at,received_at,
                  funds_json,positions_json,holdings_json,evidence_hash)
                values (?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)
                on conflict (user_id,broker_account_id,observed_at,evidence_hash) do nothing
                """, snapshotId, userId, snapshot.accountId(), snapshot.brokerId().value(),
                Timestamp.from(snapshot.observedAt()), Timestamp.from(snapshot.receivedAt()),
                funds, positions, holdings, hash);
        if (inserted == 0) {
            return findExact(userId, snapshot.accountId(), snapshot.observedAt(), hash).orElseThrow();
        }
        return new BrokerAccountEvidence(snapshotId, snapshot, hash);
    }

    @Override
    public Optional<BrokerAccountEvidence> latestAtOrBefore(
            UUID userId, String brokerAccountId, Instant knowledgeCutoff) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        return query("""
                select * from broker_evidence.account_snapshot
                 where user_id=? and broker_account_id=? and received_at<=?
                 order by received_at desc,observed_at desc limit 1
                """, userId, requiredAccount(brokerAccountId), Timestamp.from(knowledgeCutoff));
    }

    private Optional<BrokerAccountEvidence> findExact(
            UUID userId, String accountId, Instant observedAt, String hash) {
        return query("""
                select * from broker_evidence.account_snapshot
                 where user_id=? and broker_account_id=? and observed_at=? and evidence_hash=?
                 limit 1
                """, userId, accountId, Timestamp.from(observedAt), hash);
    }

    private Optional<BrokerAccountEvidence> query(String sql, Object... args) {
        return jdbc.query(sql, (result, row) -> {
            try {
                Map<String, BrokerFundsSegment> funds = json.readValue(result.getString("funds_json"), FUNDS);
                var positions = json.readTree(result.getString("positions_json"));
                List<BrokerPosition> net = json.readValue(positions.path("net").toString(), POSITIONS);
                List<BrokerPosition> day = json.readValue(positions.path("day").toString(), POSITIONS);
                List<BrokerHolding> holdings = json.readValue(result.getString("holdings_json"), HOLDINGS);
                var snapshot = new BrokerAccountSnapshot(new BrokerId(result.getString("broker")),
                        result.getString("broker_account_id"), funds, net, day, holdings,
                        result.getTimestamp("observed_at").toInstant(),
                        result.getTimestamp("received_at").toInstant());
                return new BrokerAccountEvidence(result.getObject("snapshot_id", UUID.class), snapshot,
                        result.getString("evidence_hash"));
            } catch (Exception failure) {
                throw new IllegalStateException("Stored broker account evidence could not be parsed", failure);
            }
        }, args).stream().findFirst();
    }

    private String write(Object value) {
        try {
            return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Broker account evidence could not be serialized", failure);
        }
    }

    private static String hash(UUID userId, BrokerAccountSnapshot snapshot,
            String funds, String positions, String holdings) {
        String canonical = userId + "|" + snapshot.accountId() + "|" + snapshot.brokerId().value()
                + "|" + snapshot.observedAt() + "|" + snapshot.receivedAt()
                + "|" + funds + "|" + positions + "|" + holdings;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static String requiredAccount(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Broker account id is required");
        return value.trim();
    }
}
