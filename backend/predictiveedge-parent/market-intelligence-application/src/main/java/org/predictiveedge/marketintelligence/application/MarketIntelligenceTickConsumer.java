package org.predictiveedge.marketintelligence.application;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.predictiveedge.broker.connection.UserMarketDataListener;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.marketintelligence.domain.BarFinalityPolicy;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSession;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.MarketSessionPhase;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

/** Reorders normalized ticks by event time and publishes final or corrected causal bars. */
public final class MarketIntelligenceTickConsumer implements UserMarketDataListener {
    private static final Comparator<MarketTick> EVENT_ORDER = Comparator
            .comparing(MarketTick::exchangeTimestamp)
            .thenComparing(MarketTick::receivedAt)
            .thenComparing(MarketTick::providerInstrumentId)
            .thenComparing(MarketTick::lastPrice);

    private final MarketSessionPort sessions;
    private final MarketBarPublicationPort publications;
    private final MarketTickRejectionPort rejections;
    private final MarketDepthPublicationPort depthPublications;
    private final MarketIntelligenceMetricsPort metrics;
    private final List<BarTimeframe> timeframes;
    private final BarFinalityPolicy finalityPolicy;
    private final String aggregationPolicyVersion;
    private final Map<FeedKey, FeedState> feeds = new ConcurrentHashMap<>();

    public MarketIntelligenceTickConsumer(
            MarketSessionPort sessions,
            MarketBarPublicationPort publications,
            MarketTickRejectionPort rejections,
            Set<BarTimeframe> timeframes,
            BarFinalityPolicy finalityPolicy,
            String aggregationPolicyVersion) {
        this(sessions, publications, rejections, MarketDepthPublicationPort.noop(),
                MarketIntelligenceMetricsPort.noop(), timeframes,
                finalityPolicy, aggregationPolicyVersion);
    }

    public MarketIntelligenceTickConsumer(
            MarketSessionPort sessions,
            MarketBarPublicationPort publications,
            MarketTickRejectionPort rejections,
            MarketIntelligenceMetricsPort metrics,
            Set<BarTimeframe> timeframes,
            BarFinalityPolicy finalityPolicy,
            String aggregationPolicyVersion) {
        this(sessions, publications, rejections, MarketDepthPublicationPort.noop(), metrics,
                timeframes, finalityPolicy, aggregationPolicyVersion);
    }

    public MarketIntelligenceTickConsumer(
            MarketSessionPort sessions,
            MarketBarPublicationPort publications,
            MarketTickRejectionPort rejections,
            MarketDepthPublicationPort depthPublications,
            MarketIntelligenceMetricsPort metrics,
            Set<BarTimeframe> timeframes,
            BarFinalityPolicy finalityPolicy,
            String aggregationPolicyVersion) {
        this.sessions = Objects.requireNonNull(sessions, "Market session port is required");
        this.publications = Objects.requireNonNull(publications, "Market bar publication port is required");
        this.rejections = Objects.requireNonNull(rejections, "Tick rejection port is required");
        this.depthPublications = Objects.requireNonNull(depthPublications, "Market depth publication port is required");
        this.metrics = Objects.requireNonNull(metrics, "Market-intelligence metrics port is required");
        Objects.requireNonNull(timeframes, "Bar timeframes are required");
        if (timeframes.isEmpty()) throw new IllegalArgumentException("At least one bar timeframe is required");
        this.timeframes = List.copyOf(EnumSet.copyOf(timeframes));
        this.finalityPolicy = Objects.requireNonNull(finalityPolicy, "Bar finality policy is required");
        if (aggregationPolicyVersion == null || aggregationPolicyVersion.isBlank())
            throw new IllegalArgumentException("Aggregation policy version is required");
        this.aggregationPolicyVersion = aggregationPolicyVersion.trim();
    }

    @Override
    public void onTicks(UUID userId, String brokerAccountId, List<MarketTick> ticks) {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(ticks, "Market ticks are required");
        ticks.forEach(tick -> {
            MarketTick required = Objects.requireNonNull(tick);
            metrics.tickReceived(required);
            accept(userId, brokerAccountId, required);
        });
    }

    @Override
    public void onStateChanged(UUID userId, String brokerAccountId, MarketDataStreamState state) {
        metrics.streamStateChanged(Objects.requireNonNull(state, "Market-data stream state is required"));
    }

    @Override
    public void onFailure(UUID userId, String brokerAccountId, RuntimeException failure) {
        Objects.requireNonNull(failure, "Market-data stream failure is required");
        metrics.streamFailed();
    }

    /** Advances event time without fabricating a market tick, for example at a governed session boundary. */
    public void advanceWatermark(UUID userId, String brokerAccountId, Instrument instrument,
            MarketSessionId sessionId, Instant eventTimeWatermark, Instant availableAt) {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        Objects.requireNonNull(sessionId, "Market session id is required");
        Objects.requireNonNull(eventTimeWatermark, "Event-time watermark is required");
        Objects.requireNonNull(availableAt, "Watermark availability is required");
        if (availableAt.isBefore(eventTimeWatermark))
            throw new IllegalArgumentException("Watermark cannot be available before its event time");
        feeds.forEach((key, state) -> {
            if (!key.userId().equals(userId) || !key.accountId().equals(brokerAccountId)
                    || !key.instrument().equals(instrument) || !key.sessionId().equals(sessionId)) return;
            synchronized (state) {
                state.advanceWatermark(eventTimeWatermark);
                finalizeReady(state, userId, brokerAccountId, availableAt);
            }
        });
    }

    /** Releases completed in-memory session ledgers once the configured correction-retention window has elapsed. */
    public int evictSessionsEndedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "Session retention cutoff is required");
        int before = feeds.size();
        feeds.entrySet().removeIf(entry -> entry.getValue().session.sessionEnd().isBefore(cutoff));
        return before - feeds.size();
    }

    private void accept(UUID userId, String accountId, MarketTick tick) {
        Optional<MarketSession> resolved = sessions.sessionFor(tick.instrument(), tick.exchangeTimestamp());
        if (resolved.isEmpty()) {
            reject(userId, accountId, tick, MarketTickRejection.Reason.SESSION_UNAVAILABLE,
                    "No effective market session covers the exchange timestamp");
            return;
        }
        MarketSession session = resolved.orElseThrow();
        var key = new FeedKey(userId, accountId, tick.instrument(), session.id(), subjectType(tick));
        if (session.phaseAt(tick.exchangeTimestamp()).orElse(null) != MarketSessionPhase.CONTINUOUS) {
            var existing = feeds.get(key);
            if (existing != null) {
                synchronized (existing) {
                    existing.advanceWatermark(tick.exchangeTimestamp());
                    finalizeReady(existing, userId, accountId, tick.receivedAt());
                }
            }
            reject(userId, accountId, tick, MarketTickRejection.Reason.OUTSIDE_CONTINUOUS_TRADING,
                    "Tick did not occur during a continuous-trading phase");
            return;
        }

        var state = feeds.computeIfAbsent(key,
                ignored -> new FeedState(session, key.subject(), key.userId(), key.accountId()));
        synchronized (state) {
            if (!state.seen.add(tick)) {
                reject(userId, accountId, tick, MarketTickRejection.Reason.DUPLICATE,
                        "An identical normalized tick was already admitted");
                return;
            }
            state.samples.add(tick);
            state.samples.sort(EVENT_ORDER);
            if (tick instanceof EquityMarketTick equity) {
                depthPublications.publish(userId, accountId, equity);
            }
            state.advanceWatermark(tick.exchangeTimestamp());
            correctPublishedBars(state, userId, accountId, tick);
            finalizeReady(state, userId, accountId, tick.receivedAt());
        }
    }

    private void finalizeReady(FeedState state, UUID userId, String accountId, Instant triggeredAt) {
        for (BarTimeframe timeframe : timeframes) {
            for (BarInterval interval : intervals(state, timeframe)) {
                var key = new MarketBarKey(state.subject, state.session.id(), timeframe, interval);
                if (state.revisions.containsKey(key)
                        || !finalityPolicy.canFinalize(interval, state.eventTimeWatermark)) continue;
                calculate(state, key).ifPresent(calculated -> {
                    Instant availableAt = latest(triggeredAt, calculated.latestReceipt(), calculated.observedThrough());
                    var revision = new MarketBarRevision(key, 1, calculated.values(), calculated.observedThrough(),
                            BarFinalityState.FINAL, availableAt, null, calculated.contentHash(),
                            aggregationPolicyVersion, finalityPolicy.version());
                    publications.publish(userId, accountId, revision);
                    metrics.barPublished(revision);
                    state.revisions.put(key, revision);
                });
            }
        }
    }

    private void correctPublishedBars(FeedState state, UUID userId, String accountId, MarketTick admittedTick) {
        var current = new ArrayList<>(state.revisions.entrySet());
        current.sort(Map.Entry.comparingByKey(Comparator
                .comparing((MarketBarKey key) -> key.interval().startsAt())
                .thenComparing(MarketBarKey::timeframe)));
        for (var entry : current) {
            calculate(state, entry.getKey()).ifPresent(calculated -> {
                var previous = entry.getValue();
                if (previous.values().equals(calculated.values())
                        && previous.inputManifestHash().equals(calculated.contentHash())) return;
                Instant correctedAt = latest(admittedTick.receivedAt(), previous.availableAt(),
                        calculated.observedThrough(), calculated.latestReceipt());
                var corrected = previous.correct(calculated.values(), calculated.observedThrough(), correctedAt,
                        "LATE_OR_OUT_OF_ORDER_TICK", calculated.contentHash());
                publications.publish(userId, accountId, corrected);
                metrics.barPublished(corrected);
                state.revisions.put(entry.getKey(), corrected);
            });
        }
    }

    private Optional<CalculatedBar> calculate(FeedState state, MarketBarKey key) {
        var inBar = state.samples.stream()
                .filter(tick -> key.interval().contains(tick.exchangeTimestamp()))
                .toList();
        if (inBar.isEmpty()) return Optional.empty();
        BigDecimal open = inBar.getFirst().lastPrice();
        BigDecimal close = inBar.getLast().lastPrice();
        BigDecimal high = inBar.stream().map(MarketTick::lastPrice).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = inBar.stream().map(MarketTick::lastPrice).min(BigDecimal::compareTo).orElseThrow();
        long volume = volume(state, key, inBar);
        if (volume < 0) return Optional.empty();
        Instant observedThrough = inBar.getLast().exchangeTimestamp();
        Instant latestReceipt = inBar.stream().map(MarketTick::receivedAt).max(Instant::compareTo).orElseThrow();
        return Optional.of(new CalculatedBar(new MarketBarValues(open, high, low, close, volume),
                observedThrough, latestReceipt, hash(key, state.samples, inBar)));
    }

    private long volume(FeedState state, MarketBarKey key, List<MarketTick> inBar) {
        if (state.subject.type() == ObservationSubjectType.INDEX) return 0;
        var last = (EquityMarketTick) inBar.getLast();
        long baseline = state.samples.stream()
                .filter(EquityMarketTick.class::isInstance)
                .map(EquityMarketTick.class::cast)
                .filter(tick -> tick.exchangeTimestamp().isBefore(key.interval().startsAt()))
                .max(EVENT_ORDER)
                .map(EquityMarketTick::cumulativeVolume)
                .orElse(0L);
        long volume = last.cumulativeVolume() - baseline;
        if (volume < 0) {
            var rejected = inBar.getLast();
            reject(state.userId, state.accountId, rejected,
                    MarketTickRejection.Reason.INVALID_CUMULATIVE_VOLUME,
                    "Cumulative volume moved below the session baseline");
        }
        return volume;
    }

    private List<BarInterval> intervals(FeedState state, BarTimeframe timeframe) {
        return state.samples.stream()
                .map(tick -> state.session.barIntervalAt(tick.exchangeTimestamp(), timeframe))
                .flatMap(Optional::stream)
                .distinct()
                .sorted(Comparator.comparing(BarInterval::startsAt))
                .toList();
    }

    private static ContentHash hash(MarketBarKey key, List<MarketTick> all, List<MarketTick> inBar) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            add(digest, key.subject().type().name());
            add(digest, key.subject().id());
            add(digest, key.sessionId().toString());
            add(digest, key.timeframe().name());
            add(digest, key.interval().startsAt().toString());
            add(digest, key.interval().endsAt().toString());
            all.stream().filter(EquityMarketTick.class::isInstance)
                    .map(EquityMarketTick.class::cast)
                    .filter(tick -> tick.exchangeTimestamp().isBefore(key.interval().startsAt()))
                    .max(EVENT_ORDER)
                    .ifPresent(tick -> add(digest, "BASELINE:" + tick.cumulativeVolume()));
            for (MarketTick tick : inBar) add(digest, fingerprint(tick));
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String fingerprint(MarketTick tick) {
        long volume = tick instanceof EquityMarketTick equity ? equity.cumulativeVolume() : 0L;
        return tick.providerInstrumentId() + "|" + tick.exchangeTimestamp() + "|" + tick.receivedAt()
                + "|" + tick.lastPrice().toPlainString() + "|" + volume;
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static ObservationSubjectType subjectType(MarketTick tick) {
        return tick instanceof IndexMarketTick ? ObservationSubjectType.INDEX : ObservationSubjectType.INSTRUMENT;
    }

    private static ObservationSubject subject(Instrument instrument, ObservationSubjectType type) {
        return new ObservationSubject(type, instrument.exchange() + ":" + instrument.symbol());
    }

    private static Instant latest(Instant first, Instant... remaining) {
        Instant latest = Objects.requireNonNull(first);
        for (Instant value : remaining) if (value.isAfter(latest)) latest = value;
        return latest;
    }

    private void reject(UUID userId, String accountId, MarketTick tick,
            MarketTickRejection.Reason reason, String detail) {
        var rejection = new MarketTickRejection(userId, accountId, tick, reason, detail);
        rejections.reject(rejection);
        metrics.tickRejected(rejection);
    }

    private record FeedKey(UUID userId, String accountId, Instrument instrument,
                           MarketSessionId sessionId, ObservationSubjectType subjectType) {
        private ObservationSubject subject() { return MarketIntelligenceTickConsumer.subject(instrument, subjectType); }
    }

    private static final class FeedState {
        private final MarketSession session;
        private final ObservationSubject subject;
        private final UUID userId;
        private final String accountId;
        private final List<MarketTick> samples = new ArrayList<>();
        private final Set<MarketTick> seen = new HashSet<>();
        private final Map<MarketBarKey, MarketBarRevision> revisions = new HashMap<>();
        private Instant eventTimeWatermark = Instant.MIN;

        private FeedState(MarketSession session, ObservationSubject subject, UUID userId, String accountId) {
            this.session = session;
            this.subject = subject;
            this.userId = userId;
            this.accountId = accountId;
        }

        private void advanceWatermark(Instant eventTime) {
            if (eventTime.isAfter(eventTimeWatermark)) eventTimeWatermark = eventTime;
        }
    }

    private record CalculatedBar(MarketBarValues values, Instant observedThrough,
                                 Instant latestReceipt, ContentHash contentHash) {}
}
