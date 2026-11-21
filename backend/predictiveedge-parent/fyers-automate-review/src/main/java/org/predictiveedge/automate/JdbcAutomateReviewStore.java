package org.predictiveedge.automate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL store for the automate schema (V018). */
public class JdbcAutomateReviewStore implements AutomateReviewStore {
    private final JdbcTemplate jdbc;

    public JdbcAutomateReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void saveWebhook(StoredWebhook w) {
        jdbc.update("""
                insert into automate.webhook(webhook_id,user_id,label,encrypted_url,url_fingerprint,masked_url,enabled,created_at,last_delivered_at)
                values (?,?,?,?,?,?,?,?,?)
                """, w.webhookId(), w.userId(), w.label(), w.encryptedUrl(), w.urlFingerprint(), w.maskedUrl(),
                w.enabled(), ts(w.createdAt()), ts(w.lastDeliveredAt()));
    }

    @Override
    public List<StoredWebhook> webhooks(UUID userId) {
        return jdbc.query("select * from automate.webhook where user_id=? order by created_at", (rs, i) -> webhook(rs), userId);
    }

    @Override
    public Optional<StoredWebhook> webhook(UUID userId, UUID webhookId) {
        return jdbc.query("select * from automate.webhook where user_id=? and webhook_id=?", (rs, i) -> webhook(rs),
                userId, webhookId).stream().findFirst();
    }

    @Override
    public void setWebhookEnabled(UUID userId, UUID webhookId, boolean enabled) {
        jdbc.update("update automate.webhook set enabled=? where user_id=? and webhook_id=?", enabled, userId, webhookId);
    }

    @Override
    public void markWebhookDelivered(UUID webhookId, Instant deliveredAt) {
        jdbc.update("update automate.webhook set last_delivered_at=? where webhook_id=?", ts(deliveredAt), webhookId);
    }

    @Override
    public void deleteWebhook(UUID userId, UUID webhookId) {
        jdbc.update("delete from automate.webhook where user_id=? and webhook_id=?", userId, webhookId);
    }

    @Override
    @Transactional
    public void createReview(RecommendationReview r) {
        jdbc.update("""
                insert into automate.recommendation_review(review_id,user_id,exchange,symbol,source_ref,status,created_at,updated_at)
                values (?,?,?,?,?,?,?,?)
                """, r.reviewId(), r.userId(), r.exchange(), r.symbol(), r.sourceRef(), r.status().name(),
                ts(r.createdAt()), ts(r.updatedAt()));
        r.versions().forEach(v -> addVersion(r.reviewId(), v));
    }

    @Override
    public void updateStatus(UUID reviewId, ReviewStatus status, Instant updatedAt) {
        jdbc.update("update automate.recommendation_review set status=?, updated_at=? where review_id=?",
                status.name(), ts(updatedAt), reviewId);
    }

    @Override
    public void addVersion(UUID reviewId, RecommendationVersion v) {
        RecommendationDraft d = v.draft();
        jdbc.update("""
                insert into automate.recommendation_version(version_id,review_id,stage,action,entry_low,entry_high,stop_loss,
                  target_one,target_two,quantity,confidence,rationale,source,created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
                    ps.setObject(1, v.versionId()); ps.setObject(2, reviewId); ps.setString(3, v.stage().name());
                    ps.setString(4, d.action().name()); ps.setBigDecimal(5, d.entryLow()); ps.setBigDecimal(6, d.entryHigh());
                    ps.setBigDecimal(7, d.stopLoss()); ps.setBigDecimal(8, d.targetOne()); ps.setBigDecimal(9, d.targetTwo());
                    if (d.quantity() == null) ps.setNull(10, Types.INTEGER); else ps.setInt(10, d.quantity());
                    if (d.confidence() == null) ps.setNull(11, Types.INTEGER); else ps.setInt(11, d.confidence());
                    ps.setString(12, d.rationale()); ps.setString(13, v.source()); ps.setTimestamp(14, ts(v.createdAt()));
                });
    }

    @Override
    public void addDelivery(WebhookDelivery d, String payloadJson) {
        jdbc.update("""
                insert into automate.webhook_delivery(delivery_id,review_id,webhook_id,stage,sent_at,http_status,succeeded,error,payload_json)
                values (?,?,?,?,?,?,?,?,cast(? as jsonb))
                """, ps -> {
                    ps.setObject(1, d.deliveryId()); ps.setObject(2, d.reviewId()); ps.setObject(3, d.webhookId());
                    ps.setString(4, d.stage() == null ? null : d.stage().name()); ps.setTimestamp(5, ts(d.sentAt()));
                    if (d.httpStatus() == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, d.httpStatus());
                    ps.setBoolean(7, d.succeeded()); ps.setString(8, d.error()); ps.setString(9, payloadJson);
                });
    }

    @Override
    public void saveOutcome(UUID reviewId, TradeOutcome o) {
        jdbc.update("""
                insert into automate.trade_outcome(review_id,direction,quantity,entry_price,exit_price,entered_at,exited_at,
                  charges,realized_pnl,followed_stage,monitoring_case_id,notes,recorded_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, reviewId, o.direction().name(), o.quantity(), o.entryPrice(), o.exitPrice(), ts(o.enteredAt()),
                ts(o.exitedAt()), o.charges(), o.realizedPnl(), o.followedStage().name(), o.monitoringCaseId(),
                o.notes(), ts(o.recordedAt()));
    }

    @Override
    public Optional<RecommendationReview> review(UUID userId, UUID reviewId) {
        return load("r.user_id=? and r.review_id=?", List.of(userId, reviewId), 1).stream().findFirst();
    }

    @Override
    public List<RecommendationReview> reviews(UUID userId, String symbol, int limit) {
        return symbol == null ? load("r.user_id=?", List.of(userId), limit)
                : load("r.user_id=? and r.symbol=?", List.of(userId, symbol), limit);
    }

    @Override
    public List<RecommendationReview> closedReviews(UUID userId, String symbol, int limit) {
        String where = "r.user_id=? and exists (select 1 from automate.trade_outcome o where o.review_id=r.review_id)";
        return symbol == null ? load(where, List.of(userId), limit)
                : load(where + " and r.symbol=?", List.of(userId, symbol), limit);
    }

    private List<RecommendationReview> load(String where, List<Object> args, int limit) {
        List<Object> params = new ArrayList<>(args);
        params.add(limit);
        Map<UUID, Header> headers = new LinkedHashMap<>();
        jdbc.query("select r.* from automate.recommendation_review r where " + where
                + " order by r.created_at desc limit ?", rs -> {
                    UUID id = rs.getObject("review_id", UUID.class);
                    headers.put(id, new Header(id, rs.getObject("user_id", UUID.class), rs.getString("exchange"),
                            rs.getString("symbol"), rs.getString("source_ref"), ReviewStatus.valueOf(rs.getString("status")),
                            instant(rs, "created_at"), instant(rs, "updated_at")));
                }, params.toArray());
        if (headers.isEmpty()) return List.of();
        UUID[] ids = headers.keySet().toArray(UUID[]::new);
        Map<UUID, List<RecommendationVersion>> versions = new LinkedHashMap<>();
        Map<UUID, List<WebhookDelivery>> deliveries = new LinkedHashMap<>();
        Map<UUID, TradeOutcome> outcomes = new LinkedHashMap<>();
        jdbc.query(con -> {
            var ps = con.prepareStatement("select * from automate.recommendation_version where review_id = any(?) order by created_at");
            ps.setArray(1, con.createArrayOf("uuid", ids));
            return ps;
        }, rs -> {
            versions.computeIfAbsent(rs.getObject("review_id", UUID.class), k -> new ArrayList<>()).add(new RecommendationVersion(
                    rs.getObject("version_id", UUID.class), RecommendationStage.valueOf(rs.getString("stage")),
                    new RecommendationDraft(TradeAction.valueOf(rs.getString("action")), rs.getBigDecimal("entry_low"),
                            rs.getBigDecimal("entry_high"), rs.getBigDecimal("stop_loss"), rs.getBigDecimal("target_one"),
                            rs.getBigDecimal("target_two"), integer(rs, "quantity"), integer(rs, "confidence"),
                            rs.getString("rationale")),
                    rs.getString("source"), instant(rs, "created_at")));
        });
        jdbc.query(con -> {
            var ps = con.prepareStatement("select * from automate.webhook_delivery where review_id = any(?) order by sent_at");
            ps.setArray(1, con.createArrayOf("uuid", ids));
            return ps;
        }, rs -> {
            String stage = rs.getString("stage");
            deliveries.computeIfAbsent(rs.getObject("review_id", UUID.class), k -> new ArrayList<>()).add(new WebhookDelivery(
                    rs.getObject("delivery_id", UUID.class), rs.getObject("review_id", UUID.class),
                    rs.getObject("webhook_id", UUID.class), stage == null ? null : RecommendationStage.valueOf(stage),
                    instant(rs, "sent_at"), integer(rs, "http_status"), rs.getBoolean("succeeded"), rs.getString("error")));
        });
        jdbc.query(con -> {
            var ps = con.prepareStatement("select * from automate.trade_outcome where review_id = any(?)");
            ps.setArray(1, con.createArrayOf("uuid", ids));
            return ps;
        }, rs -> {
            BigDecimal charges = rs.getBigDecimal("charges");
            outcomes.put(rs.getObject("review_id", UUID.class), new TradeOutcome(TradeAction.valueOf(rs.getString("direction")),
                    rs.getBigDecimal("quantity"), rs.getBigDecimal("entry_price"), rs.getBigDecimal("exit_price"),
                    instant(rs, "entered_at"), instant(rs, "exited_at"), charges,
                    RecommendationStage.valueOf(rs.getString("followed_stage")), rs.getObject("monitoring_case_id", UUID.class),
                    rs.getString("notes"), instant(rs, "recorded_at")));
        });
        return headers.values().stream().map(h -> new RecommendationReview(h.id, h.userId, h.exchange, h.symbol, h.sourceRef,
                h.status, h.createdAt, h.updatedAt, versions.getOrDefault(h.id, List.of()),
                deliveries.getOrDefault(h.id, List.of()), outcomes.get(h.id))).toList();
    }

    private record Header(UUID id, UUID userId, String exchange, String symbol, String sourceRef, ReviewStatus status,
            Instant createdAt, Instant updatedAt) { }

    private static StoredWebhook webhook(ResultSet rs) throws SQLException {
        return new StoredWebhook(rs.getObject("webhook_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("label"), rs.getString("encrypted_url"), rs.getString("url_fingerprint"),
                rs.getString("masked_url"), rs.getBoolean("enabled"), instant(rs, "created_at"),
                instant(rs, "last_delivered_at"));
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
}
