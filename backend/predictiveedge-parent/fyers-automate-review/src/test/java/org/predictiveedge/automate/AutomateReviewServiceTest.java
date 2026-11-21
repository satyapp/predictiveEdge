package org.predictiveedge.automate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.CredentialCipher;

class AutomateReviewServiceTest {
    private static final UUID USER = UUID.randomUUID();
    private static final String URL = "https://api-t2.fyers.in/automate/webhook?id=8c1d2e";

    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-23T04:00:00Z"));
    private final InMemoryStore store = new InMemoryStore();
    private final List<String> posted = new ArrayList<>();
    private final List<URI> postedTo = new ArrayList<>();
    private int nextStatus = 200;
    private AutomateReviewService service;

    @BeforeEach
    void setUp() {
        CredentialCipher cipher = new CredentialCipher() {
            public String encrypt(String plaintext) { return "enc:" + plaintext; }
            public String decrypt(String ciphertext) { return ciphertext.substring(4); }
        };
        AutomateWebhookSender sender = (uri, payload) -> {
            postedTo.add(uri);
            posted.add(payload);
            return new AutomateWebhookSender.Result(nextStatus, null);
        };
        service = new AutomateReviewService(store, cipher, sender, new DeterministicReconciliationEngine(),
                GuardianAdvisor.NONE, new AutomatePayloads(json, "http://localhost:3000/"),
                new WebhookUrlPolicy(List.of("fyers.in")), Duration.ofSeconds(60), clock, UUID::randomUUID);
    }

    @Test
    void fullLoopSendsReviewOnlyPayloadsAndClosesWithRealizedPnl() throws Exception {
        AutomateWebhook webhook = service.registerWebhook(USER, "SBIN review", URL);
        assertThat(webhook.maskedUrl()).doesNotContain("automate/webhook");

        RecommendationReview review = service.createStageOne(USER, "NSE", "sbin", "intraday:BREAKOUTS",
                buy("800", "790", "820", 50, 70, "Breakout above range high with volume."), "PredictiveEdge level 1 AI",
                webhook.webhookId());
        assertThat(review.status()).isEqualTo(ReviewStatus.AWAITING_FIA_REVIEW);
        assertThat(review.symbol()).isEqualTo("SBIN");
        assertThat(postedTo).containsExactly(URI.create(URL));
        JsonNode stageOne = json.readTree(posted.getFirst());
        assertThat(stageOne.path("review_only").asBoolean()).isTrue();
        assertThat(stageOne.path("live_order").asBoolean()).isFalse();
        assertThat(stageOne.path("symbol").asText()).isEqualTo("NSE:SBIN-EQ");
        assertThat(stageOne.path("stage").asText()).isEqualTo("PREDICTIVEEDGE_STAGE_ONE");
        assertThat(stageOne.has("lots")).isFalse();

        review = service.recordFiaReview(USER, review.reviewId(),
                buy("802", "794", "815", 40, 60, "FIA: RSI stretched, tighten the stop and take profit nearer."));
        assertThat(review.status()).isEqualTo(ReviewStatus.FIA_REVIEW_RECORDED);

        review = service.buildFinal(USER, review.reviewId());
        RecommendationDraft fin = review.latest(RecommendationStage.FINAL_AI_FIA).orElseThrow().draft();
        assertThat(fin.action()).isEqualTo(TradeAction.BUY);
        assertThat(fin.stopLoss()).isEqualByComparingTo("794");
        assertThat(fin.targetOne()).isEqualByComparingTo("815");
        assertThat(fin.quantity()).isEqualTo(40);
        assertThat(review.status()).isEqualTo(ReviewStatus.FINAL_READY);

        clock.advance(Duration.ofSeconds(61));
        WebhookDelivery delivery = service.deliver(USER, review.reviewId(), RecommendationStage.FINAL_AI_FIA, webhook.webhookId());
        assertThat(delivery.succeeded()).isTrue();
        assertThat(json.readTree(posted.getLast()).path("stage").asText()).isEqualTo("FINAL_AI_FIA");
        assertThat(service.review(USER, review.reviewId()).status()).isEqualTo(ReviewStatus.FINAL_SENT);

        review = service.recordOutcome(USER, review.reviewId(), TradeAction.BUY, new BigDecimal("40"),
                new BigDecimal("802"), new BigDecimal("812.5"), clock.instant().minusSeconds(3600),
                clock.instant().minusSeconds(60), new BigDecimal("20"), null, null, "Exited at target one");
        assertThat(review.status()).isEqualTo(ReviewStatus.CLOSED);
        assertThat(review.outcome().realizedPnl()).isEqualByComparingTo("400.00");
        assertThat(review.outcome().followedStage()).isEqualTo(RecommendationStage.FINAL_AI_FIA);

        LearningSummary learning = service.learning(USER, "SBIN");
        assertThat(learning.closedTrades()).isEqualTo(1);
        assertThat(learning.winningTrades()).isEqualTo(1);
        assertThat(learning.fiaAgreedWithStageOne()).isEqualTo(1);
        assertThat(learning.stageOneDirectionCorrect()).isEqualTo(1);
    }

    @Test
    void secondCallWithinAMinuteIsRateLimitedPerWebhook() {
        AutomateWebhook webhook = service.registerWebhook(USER, null, URL);
        RecommendationReview review = service.createStageOne(USER, "NSE", "INFY", null,
                buy("1500", "1480", "1540", null, null, "x"), null, webhook.webhookId());
        assertThatThrownBy(() -> service.deliver(USER, review.reviewId(), RecommendationStage.PREDICTIVEEDGE_STAGE_ONE,
                webhook.webhookId())).isInstanceOf(AutomateReviewFailure.class)
                .extracting(f -> ((AutomateReviewFailure) f).code()).isEqualTo(AutomateReviewFailure.Code.RATE_LIMITED);
    }

    @Test
    void failedDeliveryIsAuditedAndDoesNotAdvanceStatus() {
        nextStatus = 500;
        AutomateWebhook webhook = service.registerWebhook(USER, null, URL);
        RecommendationReview review = service.createStageOne(USER, "NSE", "TCS", null,
                buy("4000", "3960", "4080", null, null, "x"), null, webhook.webhookId());
        assertThat(review.status()).isEqualTo(ReviewStatus.STAGE_ONE_CREATED);
        assertThat(review.deliveries()).singleElement().satisfies(d -> {
            assertThat(d.succeeded()).isFalse();
            assertThat(d.httpStatus()).isEqualTo(500);
        });
    }

    @Test
    void finalNeedsFiaReviewAndFiaIsNeverSentBack() {
        RecommendationReview review = service.createStageOne(USER, "NSE", "TCS", null,
                buy("4000", "3960", "4080", null, null, "x"), null, null);
        assertThatThrownBy(() -> service.buildFinal(USER, review.reviewId())).isInstanceOf(AutomateReviewFailure.class);
        AutomateWebhook webhook = service.registerWebhook(USER, null, URL);
        assertThatThrownBy(() -> service.deliver(USER, review.reviewId(), RecommendationStage.FIA_REVIEW, webhook.webhookId()))
                .isInstanceOf(AutomateReviewFailure.class);
        assertThatThrownBy(() -> service.recordFiaReview(USER, review.reviewId(), buy("4000", "3960", "4080", null, null, " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewsAreScopedToTheirOwner() {
        RecommendationReview review = service.createStageOne(USER, "NSE", "TCS", null,
                buy("4000", "3960", "4080", null, null, "x"), null, null);
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), review.reviewId()))
                .isInstanceOf(AutomateReviewFailure.class);
    }

    @Test
    void guardianRevisionIsAdvisoryAndNeedsAFinal() {
        RecommendationReview review = service.createStageOne(USER, "NSE", "TCS", null,
                buy("4000", "3960", "4080", null, null, "x"), null, null);
        assertThatThrownBy(() -> service.recordGuardianRevision(USER, review.reviewId(), null))
                .isInstanceOf(AutomateReviewFailure.class);
        service.recordFiaReview(USER, review.reviewId(), buy("4000", "3970", "4060", null, null, "FIA agrees"));
        service.buildFinal(USER, review.reviewId());
        assertThat(service.recordGuardianRevision(USER, review.reviewId(), null).latest(RecommendationStage.GUARDIAN_REVISION)).isEmpty();
        RecommendationReview revised = service.recordGuardianRevision(USER, review.reviewId(),
                buy("4000", "3990", "4060", null, null, "Trail stop after a strong first hour"));
        assertThat(revised.current().orElseThrow().stage()).isEqualTo(RecommendationStage.GUARDIAN_REVISION);
        assertThat(revised.status()).isEqualTo(ReviewStatus.FINAL_READY);
    }

    @Test
    void rejectsNonFyersAndNonHttpsWebhooks() {
        assertThatThrownBy(() -> service.registerWebhook(USER, null, "https://evil.example.com/hook"))
                .hasMessageContaining("FYERS");
        assertThatThrownBy(() -> service.registerWebhook(USER, null, "http://api.fyers.in/hook"))
                .hasMessageContaining("https");
        assertThatThrownBy(() -> service.registerWebhook(USER, null, "https://fyers.in.evil.com/hook"))
                .hasMessageContaining("FYERS");
        service.registerWebhook(USER, null, URL);
        assertThatThrownBy(() -> service.registerWebhook(USER, null, URL)).hasMessageContaining("already");
    }

    static RecommendationDraft buy(String entry, String stop, String target, Integer qty, Integer confidence, String why) {
        return new RecommendationDraft(TradeAction.BUY, new BigDecimal(entry), new BigDecimal(entry), new BigDecimal(stop),
                new BigDecimal(target), null, qty, confidence, why);
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class InMemoryStore implements AutomateReviewStore {
        final Map<UUID, StoredWebhook> webhooks = new LinkedHashMap<>();
        final Map<UUID, RecommendationReview> reviews = new LinkedHashMap<>();
        final Map<UUID, String> payloads = new HashMap<>();

        public void saveWebhook(StoredWebhook w) { webhooks.put(w.webhookId(), w); }
        public List<StoredWebhook> webhooks(UUID userId) { return webhooks.values().stream().filter(w -> w.userId().equals(userId)).toList(); }
        public Optional<StoredWebhook> webhook(UUID userId, UUID id) { return Optional.ofNullable(webhooks.get(id)).filter(w -> w.userId().equals(userId)); }
        public void setWebhookEnabled(UUID userId, UUID id, boolean enabled) { replaceWebhook(id, enabled, null); }
        public void markWebhookDelivered(UUID id, Instant at) { replaceWebhook(id, null, at); }
        public void deleteWebhook(UUID userId, UUID id) { webhooks.remove(id); }
        private void replaceWebhook(UUID id, Boolean enabled, Instant at) {
            StoredWebhook w = webhooks.get(id);
            webhooks.put(id, new StoredWebhook(w.webhookId(), w.userId(), w.label(), w.encryptedUrl(), w.urlFingerprint(),
                    w.maskedUrl(), enabled == null ? w.enabled() : enabled, w.createdAt(), at == null ? w.lastDeliveredAt() : at));
        }
        public void createReview(RecommendationReview r) { reviews.put(r.reviewId(), r); }
        public void updateStatus(UUID id, ReviewStatus status, Instant at) { mutate(id, status, null, null, null, at); }
        public void addVersion(UUID id, RecommendationVersion v) { mutate(id, null, v, null, null, null); }
        public void addDelivery(WebhookDelivery d, String payload) {
            payloads.put(d.deliveryId(), payload);
            if (d.reviewId() != null) mutate(d.reviewId(), null, null, d, null, null);
        }
        public void saveOutcome(UUID id, TradeOutcome o) { mutate(id, null, null, null, o, null); }
        private void mutate(UUID id, ReviewStatus status, RecommendationVersion v, WebhookDelivery d, TradeOutcome o, Instant at) {
            RecommendationReview r = reviews.get(id);
            List<RecommendationVersion> versions = new ArrayList<>(r.versions());
            if (v != null) versions.add(v);
            List<WebhookDelivery> deliveries = new ArrayList<>(r.deliveries());
            if (d != null) deliveries.add(d);
            reviews.put(id, new RecommendationReview(r.reviewId(), r.userId(), r.exchange(), r.symbol(), r.sourceRef(),
                    status == null ? r.status() : status, r.createdAt(), at == null ? r.updatedAt() : at, versions, deliveries,
                    o == null ? r.outcome() : o));
        }
        public Optional<RecommendationReview> review(UUID userId, UUID id) { return Optional.ofNullable(reviews.get(id)).filter(r -> r.userId().equals(userId)); }
        public List<RecommendationReview> reviews(UUID userId, String symbol, int limit) {
            return reviews.values().stream().filter(r -> r.userId().equals(userId) && (symbol == null || r.symbol().equals(symbol)))
                    .sorted(Comparator.comparing(RecommendationReview::createdAt).reversed()).limit(limit).toList();
        }
        public List<RecommendationReview> closedReviews(UUID userId, String symbol, int limit) {
            return reviews(userId, symbol, Integer.MAX_VALUE).stream().filter(r -> r.outcome() != null).limit(limit).toList();
        }
    }
}
