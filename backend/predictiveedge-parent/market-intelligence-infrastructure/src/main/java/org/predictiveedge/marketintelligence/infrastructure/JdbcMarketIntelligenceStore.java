package org.predictiveedge.marketintelligence.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.marketintelligence.application.MarketBarPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketDepthPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketDepthQueryPort;
import org.predictiveedge.marketintelligence.application.MarketDepthSnapshot;
import org.predictiveedge.marketintelligence.application.MarketTickRejection;
import org.predictiveedge.marketintelligence.application.MarketTickRejectionPort;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.predictiveedge.platform.eventing.contract.EventEnvelope;
import org.predictiveedge.platform.eventing.contract.EventMetadata;
import org.predictiveedge.platform.eventing.contract.SchemaVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Append-only PostgreSQL store for canonical bar revisions and rejected normalized ticks. */
public class JdbcMarketIntelligenceStore implements MarketBarPublicationPort, MarketTickRejectionPort,
        MarketDepthPublicationPort, MarketDepthQueryPort {
    static final String TOPIC = "pe.market-intelligence.v1";
    static final String EVENT_TYPE = "MarketIntelligence.MarketBarRevisionPublished";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DomainEventPublisher events;
    private final Supplier<UUID> eventIds;

    public JdbcMarketIntelligenceStore(
            JdbcTemplate jdbc, ObjectMapper json, DomainEventPublisher events, Supplier<UUID> eventIds) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.events = Objects.requireNonNull(events, "Domain event publisher is required");
        this.eventIds = Objects.requireNonNull(eventIds, "Event id supplier is required");
    }

    @Override
    @Transactional
    public void publish(UUID userId, String brokerAccountId, MarketBarRevision revision) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(revision, "Market bar revision is required");
        String accountId = requiredAccount(brokerAccountId);
        var key = revision.key();
        var session = key.sessionId();
        var values = revision.values();
        jdbc.update("""
                insert into market_intelligence.market_bar_revision (
                  user_id,broker_account_id,subject_type,subject_id,venue,trading_date,session_code,
                  timeframe,interval_start,interval_end,is_truncated,revision,open_price,high_price,
                  low_price,close_price,volume,observed_through,finality_state,available_at,
                  correction_reason,input_manifest_hash,aggregation_policy_version,finality_policy_version)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, userId, accountId, key.subject().type().name(), key.subject().id(),
                session.venue(), session.tradingDate(), session.sessionCode(), key.timeframe().name(),
                Timestamp.from(key.interval().startsAt()), Timestamp.from(key.interval().endsAt()),
                key.interval().truncatedBySessionEnd(), revision.revision(), values.open(), values.high(), values.low(),
                values.close(), values.volume(), Timestamp.from(revision.observedThrough()),
                revision.finalityState().name(), Timestamp.from(revision.availableAt()), revision.correctionReason(),
                revision.inputManifestHash().value(), revision.aggregationPolicyVersion(),
                revision.finalityPolicyVersion());
        events.stage(publication(userId, accountId, revision));
    }

    @Override
    @Transactional
    public void reject(MarketTickRejection rejection) {
        Objects.requireNonNull(rejection, "Market tick rejection is required");
        var tick = rejection.tick();
        Long cumulativeVolume = tick instanceof EquityMarketTick equity ? equity.cumulativeVolume() : null;
        jdbc.update("""
                insert into market_intelligence.market_tick_rejection (
                  rejection_id,user_id,broker_account_id,venue,symbol,provider_instrument_id,last_price,
                  cumulative_volume,exchange_timestamp,received_at,rejection_reason,detail)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), rejection.userId(), requiredAccount(rejection.brokerAccountId()),
                tick.instrument().exchange(), tick.instrument().symbol(), tick.providerInstrumentId(),
                tick.lastPrice(), cumulativeVolume, Timestamp.from(tick.exchangeTimestamp()),
                Timestamp.from(tick.receivedAt()), rejection.reason().name(), rejection.detail());
    }

    @Override
    @Transactional
    public void publish(UUID userId, String brokerAccountId, EquityMarketTick tick) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(tick, "Equity market tick is required");
        String accountId = requiredAccount(brokerAccountId);
        String buyJson = writeDepth(tick.buyDepth());
        String sellJson = writeDepth(tick.sellDepth());
        String evidenceHash = depthHash(userId, accountId, tick, buyJson, sellJson);
        jdbc.update("""
                insert into market_intelligence.market_depth_snapshot (
                  snapshot_id,user_id,broker_account_id,venue,symbol,provider_instrument_id,
                  exchange_timestamp,received_at,buy_depth_json,sell_depth_json,evidence_hash)
                values (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)
                on conflict (user_id,broker_account_id,provider_instrument_id,
                             exchange_timestamp,received_at,evidence_hash) do nothing
                """, UUID.randomUUID(), userId, accountId, tick.instrument().exchange(),
                tick.instrument().symbol(), tick.providerInstrumentId(),
                Timestamp.from(tick.exchangeTimestamp()), Timestamp.from(tick.receivedAt()),
                buyJson, sellJson, evidenceHash);
    }

    @Override
    public Optional<MarketDepthSnapshot> latestAtOrBefore(
            UUID userId, String brokerAccountId, Instrument instrument, Instant knowledgeCutoff) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        List<MarketDepthSnapshot> rows = jdbc.query("""
                select snapshot_id,user_id,broker_account_id,venue,symbol,provider_instrument_id,
                       buy_depth_json::text,sell_depth_json::text,exchange_timestamp,received_at,evidence_hash
                  from market_intelligence.market_depth_snapshot
                 where user_id=? and broker_account_id=? and venue=? and symbol=? and received_at<=?
                 order by received_at desc,exchange_timestamp desc limit 1
                """, (result, row) -> new MarketDepthSnapshot(
                        result.getObject("snapshot_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("broker_account_id"),
                        new Instrument(result.getString("venue"), result.getString("symbol")),
                        result.getString("provider_instrument_id"),
                        readDepth(result.getString("buy_depth_json")),
                        readDepth(result.getString("sell_depth_json")),
                        result.getTimestamp("exchange_timestamp").toInstant(),
                        result.getTimestamp("received_at").toInstant(),
                        result.getString("evidence_hash")),
                userId, requiredAccount(brokerAccountId), instrument.exchange(), instrument.symbol(),
                Timestamp.from(knowledgeCutoff));
        return rows.stream().findFirst();
    }

    private String writeDepth(List<MarketDepthLevel> levels) {
        try {
            return json.writeValueAsString(levels);
        } catch (Exception failure) {
            throw new IllegalStateException("Market depth could not be serialized", failure);
        }
    }

    private List<MarketDepthLevel> readDepth(String value) {
        try {
            return json.readerForListOf(MarketDepthLevel.class).readValue(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Stored market depth could not be parsed", failure);
        }
    }

    private static String depthHash(UUID userId, String accountId, EquityMarketTick tick,
            String buyJson, String sellJson) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, userId);
        append(canonical, accountId);
        append(canonical, tick.instrument().exchange());
        append(canonical, tick.instrument().symbol());
        append(canonical, tick.providerInstrumentId());
        append(canonical, tick.exchangeTimestamp());
        append(canonical, tick.receivedAt());
        append(canonical, buyJson);
        append(canonical, sellJson);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private EventPublication publication(UUID userId, String accountId, MarketBarRevision revision) {
        UUID eventId = Objects.requireNonNull(eventIds.get(), "Generated event id is required");
        String aggregateId = aggregateId(userId, accountId, revision.key());
        EventMetadata metadata = new EventMetadata(
                eventId, EVENT_TYPE, new SchemaVersion(1, 0), "market-intelligence",
                "MarketBar", aggregateId, revision.revision(), aggregateId,
                revision.availableAt(), revision.key().interval().endsAt(), revision.availableAt(), null,
                revision.key().interval().endsAt(), revision.availableAt(), eventId, eventId,
                null, null, null, accountId, List.of(), revision.inputManifestHash().value());
        return new EventPublication(TOPIC,
                EventEnvelope.create(metadata, payload(userId, accountId, revision),
                        DataClassification.CONFIDENTIAL));
    }

    private ObjectNode payload(UUID userId, String accountId, MarketBarRevision revision) {
        var key = revision.key();
        var session = key.sessionId();
        var interval = key.interval();
        var values = revision.values();
        ObjectNode payload = json.createObjectNode();
        payload.put("userId", userId.toString());
        payload.put("brokerAccountId", accountId);
        payload.put("subjectType", key.subject().type().name());
        payload.put("subjectId", key.subject().id());
        payload.put("venue", session.venue());
        payload.put("tradingDate", session.tradingDate().toString());
        payload.put("sessionCode", session.sessionCode());
        payload.put("timeframe", key.timeframe().name());
        payload.put("intervalStart", interval.startsAt().toString());
        payload.put("intervalEnd", interval.endsAt().toString());
        payload.put("truncated", interval.truncatedBySessionEnd());
        payload.put("revision", revision.revision());
        payload.put("open", values.open());
        payload.put("high", values.high());
        payload.put("low", values.low());
        payload.put("close", values.close());
        payload.put("volume", values.volume());
        payload.put("observedThrough", revision.observedThrough().toString());
        payload.put("finalityState", revision.finalityState().name());
        payload.put("availableAt", revision.availableAt().toString());
        if (revision.correctionReason() != null) {
            payload.put("correctionReason", revision.correctionReason());
        }
        payload.put("inputManifestHash", revision.inputManifestHash().value());
        payload.put("aggregationPolicyVersion", revision.aggregationPolicyVersion());
        payload.put("finalityPolicyVersion", revision.finalityPolicyVersion());
        return payload;
    }

    private static String aggregateId(UUID userId, String accountId, MarketBarKey key) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, userId);
        append(canonical, accountId);
        append(canonical, key.subject().type());
        append(canonical, key.subject().id());
        append(canonical, key.sessionId().venue());
        append(canonical, key.sessionId().tradingDate());
        append(canonical, key.sessionId().sessionCode());
        append(canonical, key.timeframe());
        append(canonical, key.interval().startsAt());
        append(canonical, key.interval().endsAt());
        append(canonical, key.interval().truncatedBySessionEnd());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, Object component) {
        String value = component.toString();
        target.append(value.length()).append(':').append(value);
    }

    private static String requiredAccount(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Broker account id is required");
        return value.trim();
    }
}
