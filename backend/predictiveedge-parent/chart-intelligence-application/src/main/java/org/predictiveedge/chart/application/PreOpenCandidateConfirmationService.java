package org.predictiveedge.chart.application;

import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.math.MathContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.domain.ChartBias;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.chart.domain.ChartTrigger;
import org.predictiveedge.chart.domain.IntradaySetupAction;
import org.predictiveedge.chart.domain.IntradaySetupAssessment;
import org.predictiveedge.chart.domain.IntradaySetupType;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.EvaluationCutoff;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;

/** Confirms pre-open candidates with the finalized opening range and session VWAP, then publishes Chart evidence. */
public final class PreOpenCandidateConfirmationService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final MathContext MC = MathContext.DECIMAL64;
    private final PreOpenIntradayAnalysisService preOpen;
    private final MarketBarQueryPort bars;
    private final ChartSnapshotPublicationPort publications;
    private final Clock clock;

    public PreOpenCandidateConfirmationService(PreOpenIntradayAnalysisService preOpen, MarketBarQueryPort bars,
            ChartSnapshotPublicationPort publications, Clock clock) {
        this.preOpen = Objects.requireNonNull(preOpen, "Pre-open analysis service is required");
        this.bars = Objects.requireNonNull(bars, "Market-bar query port is required");
        this.publications = Objects.requireNonNull(publications, "Chart publication port is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Result confirm(Command command) {
        Objects.requireNonNull(command, "Confirmation command is required");
        Instant open = command.tradingDate().atTime(LocalTime.of(9, 15)).atZone(MARKET_ZONE).toInstant();
        Instant firstRangeEnd = open.plus(Duration.ofMinutes(5));
        if (command.analysisCutoff().isBefore(firstRangeEnd))
            return new Result("WAITING_FOR_FINAL_5_MINUTE_BAR", null, null, null, false);
        var stored = preOpen.analyzeStored(new PreOpenIntradayAnalysisService.StoredCommand(command.userId(),
                command.brokerAccountId(), command.venue(), command.symbol(), command.tradingDate(),
                command.analysisCutoff(), command.knowledgeCutoff(), command.historyWindow()));
        if (stored.isEmpty()) return new Result("PRE_OPEN_SNAPSHOT_NOT_FOUND", null, null, null, false);
        var assessment = stored.get().assessment();
        if (assessment.action() != IntradaySetupAction.BUY_CANDIDATE
                && assessment.action() != IntradaySetupAction.SELL_CANDIDATE) {
            return new Result("NOT_A_DIRECTIONAL_CANDIDATE", assessment, null, null, false);
        }

        var subject = new ObservationSubject(ObservationSubjectType.INSTRUMENT,
                command.venue() + ":" + command.symbol());
        var criteria = new MarketBarReplayCriteria(command.userId(), command.brokerAccountId(), subject,
                BarTimeframe.FIVE_MINUTES, open, command.analysisCutoff(),
                new EvaluationCutoff(command.analysisCutoff(), command.knowledgeCutoff()), null);
        List<MarketBarRevision> session = bars.replay(criteria, 100).stream()
                .sorted(Comparator.comparing(value -> value.key().interval().startsAt())).toList();
        List<MarketBarRevision> opening = session.stream()
                .filter(value -> !value.key().interval().endsAt().isAfter(firstRangeEnd)).toList();
        if (opening.isEmpty()) return new Result("OPENING_RANGE_NOT_FINAL", assessment, null, null, false);
        if (session.isEmpty()) return new Result("SESSION_BARS_NOT_FOUND", assessment, null, null, false);

        BigDecimal openingHigh = opening.stream().map(value -> value.values().high()).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal openingLow = opening.stream().map(value -> value.values().low()).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal vwap = vwap(session);
        BigDecimal latestClose = session.getLast().values().close();
        BigDecimal previousClose = stored.get().snapshot().observation().previousClose();
        boolean buy = assessment.action() == IntradaySetupAction.BUY_CANDIDATE;
        boolean confirmed = assessment.setupType() == IntradaySetupType.GAP_CONTINUATION
                ? buy ? latestClose.compareTo(openingHigh) > 0 && latestClose.compareTo(vwap) > 0
                      : latestClose.compareTo(openingLow) < 0 && latestClose.compareTo(vwap) < 0
                : buy ? latestClose.compareTo(previousClose) > 0 && latestClose.compareTo(vwap) > 0
                      : latestClose.compareTo(previousClose) < 0 && latestClose.compareTo(vwap) < 0;
        ChartTrigger trigger = confirmed ? buy ? ChartTrigger.UPSIDE_BREAKOUT : ChartTrigger.DOWNSIDE_BREAKDOWN
                : ChartTrigger.NONE;
        Instant availableAt = clock.instant();
        if (availableAt.isBefore(command.knowledgeCutoff())) availableAt = command.knowledgeCutoff();
        List<String> evidence = new ArrayList<>(assessment.evidenceReferences());
        evidence.add("PRE_OPEN:" + stored.get().snapshot().evidenceHash());
        session.forEach(value -> evidence.add("BAR:" + value.inputManifestHash().value()));
        String manifest = hash(evidence, assessment.action().name(), openingHigh, openingLow, vwap, latestClose);
        ChartBias direction = buy ? ChartBias.BULLISH : ChartBias.BEARISH;
        ChartBias higher = bias(assessment.ema20(), assessment.ema50());
        ChartBias sessionBias = latestClose.compareTo(vwap) > 0 ? ChartBias.BULLISH
                : latestClose.compareTo(vwap) < 0 ? ChartBias.BEARISH : ChartBias.NEUTRAL;
        ChartBias momentum = assessment.rsi14() == null ? ChartBias.NEUTRAL
                : assessment.rsi14().compareTo(new BigDecimal("55")) >= 0 ? ChartBias.BULLISH
                : assessment.rsi14().compareTo(new BigDecimal("45")) <= 0 ? ChartBias.BEARISH : ChartBias.NEUTRAL;
        boolean trendQualified = assessment.setupType() == IntradaySetupType.GAP_CONTINUATION && higher == direction;
        boolean participation = assessment.auctionVolumeRatio() != null
                && assessment.auctionVolumeRatio().compareTo(BigDecimal.ONE) >= 0;
        int confidence = confirmed && trendQualified && participation ? 70 : 50;
        var snapshot = new ChartSnapshot(UUID.nameUUIDFromBytes(manifest.getBytes(UTF_8)).toString(),
                command.venue(), command.symbol(), higher, sessionBias, momentum, trigger, trendQualified,
                true, participation, confidence, ChartReadiness.READY, true, command.analysisCutoff(),
                command.knowledgeCutoff(), availableAt, availableAt.plus(Duration.ofMinutes(5)), manifest,
                evidence.stream().distinct().toList());
        boolean appended = publications.append(command.userId(), snapshot);
        return new Result(confirmed ? "CONFIRMED" : "WAIT", assessment,
                new OpeningEvidence(openingHigh, openingLow, vwap, latestClose), snapshot, appended);
    }

    private static BigDecimal vwap(List<MarketBarRevision> bars) {
        BigDecimal weighted = BigDecimal.ZERO;
        long totalVolume = 0;
        for (var bar : bars) {
            if (bar.values().volume() == 0) continue;
            BigDecimal typical = bar.values().high().add(bar.values().low()).add(bar.values().close())
                    .divide(BigDecimal.valueOf(3), MC);
            weighted = weighted.add(typical.multiply(BigDecimal.valueOf(bar.values().volume()), MC));
            totalVolume += bar.values().volume();
        }
        if (totalVolume == 0) throw new IllegalArgumentException("Session VWAP requires non-zero volume");
        return weighted.divide(BigDecimal.valueOf(totalVolume), MC).setScale(4, HALF_UP);
    }

    private static ChartBias bias(BigDecimal fast, BigDecimal slow) {
        if (fast == null || slow == null) return ChartBias.NEUTRAL;
        return fast.compareTo(slow) > 0 ? ChartBias.BULLISH
                : fast.compareTo(slow) < 0 ? ChartBias.BEARISH : ChartBias.NEUTRAL;
    }

    private static String hash(List<String> evidence, Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            evidence.forEach(value -> digest.update(value.getBytes(UTF_8)));
            for (Object value : values) digest.update(value.toString().getBytes(UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record Command(UUID userId, String brokerAccountId, String venue, String symbol, LocalDate tradingDate,
            Instant analysisCutoff, Instant knowledgeCutoff, Duration historyWindow) {
        public Command {
            Objects.requireNonNull(userId); Objects.requireNonNull(tradingDate);
            Objects.requireNonNull(analysisCutoff); Objects.requireNonNull(knowledgeCutoff);
            Objects.requireNonNull(historyWindow);
            if (brokerAccountId == null || brokerAccountId.isBlank()) throw new IllegalArgumentException("Broker account id is required");
            if (venue == null || venue.isBlank()) throw new IllegalArgumentException("Venue is required");
            if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
            brokerAccountId = brokerAccountId.trim(); venue = venue.trim().toUpperCase(); symbol = symbol.trim().toUpperCase();
            if (analysisCutoff.isAfter(knowledgeCutoff)) throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
        }
    }

    public record OpeningEvidence(BigDecimal first5High, BigDecimal first5Low,
            BigDecimal sessionVwap, BigDecimal latestClose) { }
    public record Result(String status, IntradaySetupAssessment preOpenAssessment,
            OpeningEvidence openingEvidence, ChartSnapshot chartSnapshot, boolean appended) { }
}
