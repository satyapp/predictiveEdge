package org.predictiveedge.marketintelligence.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionQueryPort;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Append-only persistence and causal reads for final exchange pre-open auction snapshots. */
public class JdbcPreOpenAuctionStore implements PreOpenAuctionPublicationPort, PreOpenAuctionQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcPreOpenAuctionStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    @Transactional
    public PreOpenAuctionSnapshot publish(
            UUID userId, String brokerAccountId, PreOpenAuctionObservation observation, Instant receivedAt) {
        Objects.requireNonNull(userId, "User id is required");
        String accountId = required(brokerAccountId, "Broker account id");
        Objects.requireNonNull(observation, "Observation is required");
        Objects.requireNonNull(receivedAt, "Receipt timestamp is required");
        if (receivedAt.isBefore(observation.observedAt()))
            throw new IllegalArgumentException("Receipt timestamp cannot precede observation timestamp");
        String hash = evidenceHash(userId, accountId, observation, receivedAt);
        UUID snapshotId = UUID.nameUUIDFromBytes(hash.getBytes(UTF_8));
        jdbc.update("""
                insert into market_intelligence.pre_open_auction_snapshot (
                  snapshot_id,user_id,broker_account_id,venue,symbol,series,trading_date,
                  previous_close,auction_price,auction_volume,auction_turnover,best_bid,best_ask,
                  observed_at,received_at,evidence_hash)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict (snapshot_id) do nothing
                """, snapshotId, userId, accountId, observation.venue(), observation.symbol(),
                observation.series(), observation.tradingDate(), observation.previousClose(),
                observation.auctionPrice(), observation.auctionVolume(), observation.auctionTurnover(),
                observation.bestBid(), observation.bestAsk(), Timestamp.from(observation.observedAt()),
                Timestamp.from(receivedAt), hash);
        return new PreOpenAuctionSnapshot(snapshotId, userId, accountId, observation, receivedAt, hash);
    }

    @Override
    public Optional<PreOpenAuctionSnapshot> latestAtOrBefore(
            UUID userId, String brokerAccountId, String venue, String symbol, LocalDate tradingDate,
            Instant analysisCutoff, Instant knowledgeCutoff) {
        Objects.requireNonNull(userId, "User id is required");
        String accountId = required(brokerAccountId, "Broker account id");
        String normalizedVenue = required(venue, "Venue").toUpperCase(java.util.Locale.ROOT);
        String normalizedSymbol = required(symbol, "Symbol").toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(tradingDate, "Trading date is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        if (analysisCutoff.isAfter(knowledgeCutoff))
            throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
        List<PreOpenAuctionSnapshot> rows = jdbc.query("""
                select snapshot_id,user_id,broker_account_id,venue,symbol,series,trading_date,
                       previous_close,auction_price,auction_volume,auction_turnover,best_bid,best_ask,
                       observed_at,received_at,evidence_hash
                  from market_intelligence.pre_open_auction_snapshot
                 where user_id=? and broker_account_id=? and venue=? and symbol=? and trading_date=?
                   and observed_at<=? and received_at<=?
                 order by observed_at desc,received_at desc limit 1
                """, (result, row) -> {
                    var observation = new PreOpenAuctionObservation(
                            result.getString("venue"), result.getString("symbol"), result.getString("series"),
                            result.getObject("trading_date", LocalDate.class), result.getBigDecimal("previous_close"),
                            result.getBigDecimal("auction_price"), result.getLong("auction_volume"),
                            result.getBigDecimal("auction_turnover"), result.getBigDecimal("best_bid"),
                            result.getBigDecimal("best_ask"), result.getTimestamp("observed_at").toInstant());
                    return new PreOpenAuctionSnapshot(result.getObject("snapshot_id", UUID.class),
                            result.getObject("user_id", UUID.class), result.getString("broker_account_id"),
                            observation, result.getTimestamp("received_at").toInstant(),
                            result.getString("evidence_hash"));
                }, userId, accountId, normalizedVenue, normalizedSymbol, tradingDate,
                Timestamp.from(analysisCutoff), Timestamp.from(knowledgeCutoff));
        return rows.stream().findFirst();
    }

    private static String evidenceHash(
            UUID userId, String accountId, PreOpenAuctionObservation value, Instant receivedAt) {
        var canonical = new StringBuilder();
        append(canonical, userId);
        append(canonical, accountId);
        append(canonical, value.venue());
        append(canonical, value.symbol());
        append(canonical, value.series());
        append(canonical, value.tradingDate());
        append(canonical, value.previousClose());
        append(canonical, value.auctionPrice());
        append(canonical, value.auctionVolume());
        append(canonical, value.auctionTurnover());
        append(canonical, value.bestBid());
        append(canonical, value.bestAsk());
        append(canonical, value.observedAt());
        append(canonical, receivedAt);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void append(StringBuilder target, Object component) {
        String value = component instanceof BigDecimal decimal ? decimal.toPlainString() : component.toString();
        target.append(value.length()).append(':').append(value);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
