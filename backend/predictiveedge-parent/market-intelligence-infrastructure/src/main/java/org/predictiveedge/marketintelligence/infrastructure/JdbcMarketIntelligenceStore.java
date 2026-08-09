package org.predictiveedge.marketintelligence.infrastructure;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.marketintelligence.application.MarketBarPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketTickRejection;
import org.predictiveedge.marketintelligence.application.MarketTickRejectionPort;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Append-only PostgreSQL store for canonical bar revisions and rejected normalized ticks. */
public class JdbcMarketIntelligenceStore implements MarketBarPublicationPort, MarketTickRejectionPort {
    private final JdbcTemplate jdbc;

    public JdbcMarketIntelligenceStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
    }

    @Override
    @Transactional
    public void publish(UUID userId, String brokerAccountId, MarketBarRevision revision) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(revision, "Market bar revision is required");
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
                """, userId, requiredAccount(brokerAccountId), key.subject().type().name(), key.subject().id(),
                session.venue(), session.tradingDate(), session.sessionCode(), key.timeframe().name(),
                Timestamp.from(key.interval().startsAt()), Timestamp.from(key.interval().endsAt()),
                key.interval().truncatedBySessionEnd(), revision.revision(), values.open(), values.high(), values.low(),
                values.close(), values.volume(), Timestamp.from(revision.observedThrough()),
                revision.finalityState().name(), Timestamp.from(revision.availableAt()), revision.correctionReason(),
                revision.inputManifestHash().value(), revision.aggregationPolicyVersion(),
                revision.finalityPolicyVersion());
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

    private static String requiredAccount(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Broker account id is required");
        return value.trim();
    }
}
