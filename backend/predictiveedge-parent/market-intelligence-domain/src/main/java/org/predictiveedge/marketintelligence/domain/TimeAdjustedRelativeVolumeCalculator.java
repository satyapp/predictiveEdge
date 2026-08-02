package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Current final volume divided by a knowledge-dated historical median for the same session slot. */
public final class TimeAdjustedRelativeVolumeCalculator {
    private final FeatureDefinitionRef definitionRef;
    public TimeAdjustedRelativeVolumeCalculator(FeatureDefinitionRef definitionRef) {
        this.definitionRef = Objects.requireNonNull(definitionRef);
    }

    public FeatureValue calculate(FeatureDefinition definition, MarketBarRevision currentBar, int sessionSlot,
            HistoricalVolumeBaseline baseline, EvaluationCutoff cutoff, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef); Objects.requireNonNull(currentBar);
        Objects.requireNonNull(baseline); Objects.requireNonNull(cutoff); Objects.requireNonNull(computedAt);
        if (!currentBar.isEligible(cutoff) || baseline.availableAt().isAfter(cutoff.knowledgeCutoff()))
            throw new IllegalArgumentException("Relative-volume input is not causally eligible");
        if (baseline.sessionSlot() != sessionSlot || baseline.timeframe() != currentBar.key().timeframe()
                || !baseline.venue().equals(currentBar.key().sessionId().venue()))
            throw new IllegalArgumentException("Historical baseline does not match the current bar slot");
        Instant earliest = currentBar.availableAt().isAfter(baseline.availableAt())
                ? currentBar.availableAt() : baseline.availableAt();
        if (computedAt.isBefore(earliest)) throw new IllegalArgumentException("Computation precedes input availability");
        BigDecimal ratio = BigDecimal.valueOf(currentBar.values().volume())
                .divide(BigDecimal.valueOf(baseline.medianVolume()), FeatureCalculatorSupport.MATH_CONTEXT);
        return FeatureValue.readyComposite(definition, currentBar.key().subject(), currentBar.key().timeframe(), ratio,
                currentBar.key().interval().endsAt(), currentBar.key().interval().startsAt(), currentBar.observedThrough(),
                computedAt, currentBar.finalityState(), hash(currentBar, baseline, sessionSlot));
    }

    private static ContentHash hash(MarketBarRevision bar, HistoricalVolumeBaseline baseline, int slot) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            String value = bar.inputManifestHash().value() + "|" + bar.revision() + "|" + slot + "|"
                    + baseline.venue() + "|" + baseline.medianVolume() + "|" + baseline.sampleSessions() + "|"
                    + baseline.availableAt() + "|" + baseline.version();
            return new ContentHash(HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
