package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Exact ordered final-bar revisions visible to one causal feature evaluation. */
public record FeatureInputManifest(
        EvaluationCutoff cutoff,
        ObservationSubject subject,
        BarTimeframe timeframe,
        List<MarketBarRevision> bars,
        ContentHash contentHash) {

    public FeatureInputManifest {
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        Objects.requireNonNull(subject, "Feature subject is required");
        Objects.requireNonNull(timeframe, "Feature timeframe is required");
        bars = List.copyOf(Objects.requireNonNull(bars, "Feature input bars are required"));
        for (int index = 0; index < bars.size(); index++) {
            var bar = Objects.requireNonNull(bars.get(index), "Feature input bar cannot be null");
            if (!bar.key().subject().equals(subject) || bar.key().timeframe() != timeframe) {
                throw new IllegalArgumentException("Feature input bar does not match subject and timeframe");
            }
            if (!bar.isEligible(cutoff)) {
                throw new IllegalArgumentException("Feature input manifest can contain only causally eligible final bars");
            }
            if (index > 0 && !bars.get(index - 1).key().interval().startsAt()
                    .isBefore(bar.key().interval().startsAt())) {
                throw new IllegalArgumentException("Feature input bars must be strictly chronological");
            }
        }
        Objects.requireNonNull(contentHash, "Feature input content hash is required");
        if (!contentHash.equals(hash(cutoff, subject, timeframe, bars))) {
            throw new IllegalArgumentException("Feature input manifest hash does not match its contents");
        }
    }

    /** Selects the point-in-time revision for each matching bar and ignores causally invisible candidates. */
    public static FeatureInputManifest select(
            EvaluationCutoff cutoff,
            ObservationSubject subject,
            BarTimeframe timeframe,
            Collection<MarketBarRevision> candidates) {
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        Objects.requireNonNull(subject, "Feature subject is required");
        Objects.requireNonNull(timeframe, "Feature timeframe is required");
        Objects.requireNonNull(candidates, "Feature input candidates are required");
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Feature input candidates cannot contain null");
        }
        var revisionsByKey = new LinkedHashMap<MarketBarKey, List<MarketBarRevision>>();
        candidates.stream()
                .filter(bar -> bar.key().subject().equals(subject) && bar.key().timeframe() == timeframe)
                .forEach(bar -> revisionsByKey.computeIfAbsent(bar.key(), ignored -> new ArrayList<>()).add(bar));

        var selected = revisionsByKey.entrySet().stream()
                .map(entry -> PointInTimeMarketBarSelector.selectLatest(entry.getValue(), entry.getKey(), cutoff))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(bar -> bar.key().interval().startsAt()))
                .toList();
        return new FeatureInputManifest(cutoff, subject, timeframe, selected,
                hash(cutoff, subject, timeframe, selected));
    }

    private static ContentHash hash(
            EvaluationCutoff cutoff,
            ObservationSubject subject,
            BarTimeframe timeframe,
            List<MarketBarRevision> bars) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, cutoff.analysisCutoff().toString());
            add(digest, cutoff.knowledgeCutoff().toString());
            add(digest, subject.type().name());
            add(digest, subject.id());
            add(digest, timeframe.name());
            for (MarketBarRevision bar : bars) {
                add(digest, bar.key().sessionId().venue());
                add(digest, bar.key().sessionId().tradingDate().toString());
                add(digest, bar.key().sessionId().sessionCode());
                add(digest, bar.key().interval().startsAt().toString());
                add(digest, bar.key().interval().endsAt().toString());
                add(digest, Boolean.toString(bar.key().interval().truncatedBySessionEnd()));
                add(digest, Long.toString(bar.revision()));
                add(digest, decimal(bar.values().open()));
                add(digest, decimal(bar.values().high()));
                add(digest, decimal(bar.values().low()));
                add(digest, decimal(bar.values().close()));
                add(digest, Long.toString(bar.values().volume()));
                add(digest, bar.observedThrough().toString());
                add(digest, bar.finalityState().name());
                add(digest, bar.availableAt().toString());
                add(digest, bar.correctionReason() == null ? "" : bar.correctionReason());
                add(digest, bar.inputManifestHash().value());
                add(digest, bar.aggregationPolicyVersion());
                add(digest, bar.finalityPolicyVersion());
            }
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
