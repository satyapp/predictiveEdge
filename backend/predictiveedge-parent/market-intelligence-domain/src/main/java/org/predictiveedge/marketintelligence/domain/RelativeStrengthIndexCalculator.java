package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/** Wilder RSI using final closes and governed recursive rounding. */
public final class RelativeStrengthIndexCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;
    private final int period;

    public RelativeStrengthIndexCalculator(FeatureDefinitionRef definitionRef, int period) {
        this.definitionRef = Objects.requireNonNull(definitionRef);
        if (period < 2) throw new IllegalArgumentException("RSI period must be at least two");
        this.period = period;
    }

    @Override public FeatureDefinitionRef definitionRef() { return definitionRef; }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (manifest.bars().size() < period + 1) throw new IllegalStateException("RSI requires a prior close plus its seed period");
        var gains = new ArrayList<BigDecimal>(); var losses = new ArrayList<BigDecimal>();
        for (int index = 1; index < manifest.bars().size(); index++) {
            var change = manifest.bars().get(index).values().close()
                    .subtract(manifest.bars().get(index - 1).values().close());
            gains.add(change.max(BigDecimal.ZERO)); losses.add(change.negate().max(BigDecimal.ZERO));
        }
        BigDecimal averageGain = average(gains.subList(0, period), period);
        BigDecimal averageLoss = average(losses.subList(0, period), period);
        for (int index = period; index < gains.size(); index++) {
            averageGain = wilder(averageGain, gains.get(index), period);
            averageLoss = wilder(averageLoss, losses.get(index), period);
            averageGain = FeatureCalculatorSupport.recursiveValue(averageGain, definition.numericPolicy());
            averageLoss = FeatureCalculatorSupport.recursiveValue(averageLoss, definition.numericPolicy());
        }
        BigDecimal rsi;
        if (averageLoss.signum() == 0) rsi = averageGain.signum() == 0 ? BigDecimal.valueOf(50) : BigDecimal.valueOf(100);
        else {
            var relativeStrength = averageGain.divide(averageLoss, FeatureCalculatorSupport.MATH_CONTEXT);
            rsi = BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100)
                    .divide(BigDecimal.ONE.add(relativeStrength), FeatureCalculatorSupport.MATH_CONTEXT));
        }
        return FeatureValue.ready(definition, manifest, rsi, computedAt);
    }

    private static BigDecimal average(java.util.List<BigDecimal> values, int divisor) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(divisor), FeatureCalculatorSupport.MATH_CONTEXT);
    }

    private static BigDecimal wilder(BigDecimal previous, BigDecimal current, int period) {
        return previous.multiply(BigDecimal.valueOf(period - 1L), FeatureCalculatorSupport.MATH_CONTEXT)
                .add(current).divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
    }
}
