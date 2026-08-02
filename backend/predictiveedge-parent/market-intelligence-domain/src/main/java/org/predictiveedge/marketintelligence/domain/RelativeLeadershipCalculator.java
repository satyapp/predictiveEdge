package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Slope proxy: percent change in the synchronized primary/comparison close ratio. */
public final class RelativeLeadershipCalculator {
    private final FeatureDefinitionRef definitionRef; private final int period;

    public RelativeLeadershipCalculator(FeatureDefinitionRef definitionRef, int period) {
        this.definitionRef = Objects.requireNonNull(definitionRef); this.period = period;
        if (period < 2) throw new IllegalArgumentException("Leadership period must be at least two");
    }

    public FeatureValue calculate(FeatureDefinition definition, SynchronizedBarManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (manifest.primary().bars().size() < period) throw new IllegalStateException("Relative leadership is warming up");
        int firstIndex = manifest.primary().bars().size() - period;
        BigDecimal firstRatio = ratio(manifest, firstIndex); BigDecimal lastRatio = ratio(manifest,
                manifest.primary().bars().size() - 1);
        BigDecimal changePercent = lastRatio.subtract(firstRatio)
                .divide(firstRatio, FeatureCalculatorSupport.MATH_CONTEXT).multiply(BigDecimal.valueOf(100));
        var first = manifest.primary().bars().get(firstIndex); var last = manifest.primary().bars().getLast();
        Instant availability = max(computedAt, manifest.primary().bars().getLast().availableAt(),
                manifest.comparison().bars().getLast().availableAt());
        if (!availability.equals(computedAt)) throw new IllegalArgumentException("Leadership computation precedes input availability");
        BarFinalityState finality = manifest.primary().bars().stream().anyMatch(b -> b.finalityState() == BarFinalityState.CORRECTED)
                || manifest.comparison().bars().stream().anyMatch(b -> b.finalityState() == BarFinalityState.CORRECTED)
                ? BarFinalityState.CORRECTED : BarFinalityState.FINAL;
        return FeatureValue.readyComposite(definition, manifest.primary().subject(), manifest.primary().timeframe(),
                changePercent, last.key().interval().endsAt(), first.key().interval().startsAt(),
                last.observedThrough(), computedAt, finality, manifest.contentHash());
    }

    private static BigDecimal ratio(SynchronizedBarManifest manifest, int index) {
        var denominator = manifest.comparison().bars().get(index).values().close();
        if (denominator.signum() == 0) throw new IllegalStateException("Comparison close is zero");
        return manifest.primary().bars().get(index).values().close()
                .divide(denominator, FeatureCalculatorSupport.MATH_CONTEXT);
    }

    private static Instant max(Instant... values) {
        Instant result = values[0]; for (Instant value : values) if (value.isAfter(result)) result = value; return result;
    }
}
