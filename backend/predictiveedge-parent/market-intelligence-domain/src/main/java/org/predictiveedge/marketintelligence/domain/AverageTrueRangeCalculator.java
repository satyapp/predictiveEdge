package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/** Wilder ATR using final bars, a simple-average seed and governed recursive rounding. */
public final class AverageTrueRangeCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;
    private final int period;

    public AverageTrueRangeCalculator(FeatureDefinitionRef definitionRef, int period) {
        this.definitionRef = Objects.requireNonNull(definitionRef, "Feature definition reference is required");
        if (period < 2) {
            throw new IllegalArgumentException("ATR period must be at least two");
        }
        this.period = period;
    }

    @Override
    public FeatureDefinitionRef definitionRef() {
        return definitionRef;
    }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (manifest.bars().size() < period + 1) {
            throw new IllegalStateException("ATR requires a prior close plus its seed period");
        }
        var bars = manifest.bars();
        var ranges = new ArrayList<BigDecimal>();
        for (int index = 1; index < bars.size(); index++) {
            var current = bars.get(index).values();
            var priorClose = bars.get(index - 1).values().close();
            ranges.add(current.high().subtract(current.low()).max(current.high().subtract(priorClose).abs())
                    .max(current.low().subtract(priorClose).abs()));
        }
        BigDecimal atr = ranges.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        atr = FeatureCalculatorSupport.recursiveValue(atr, definition.numericPolicy());
        for (int index = period; index < ranges.size(); index++) {
            atr = atr.multiply(BigDecimal.valueOf(period - 1L), FeatureCalculatorSupport.MATH_CONTEXT)
                    .add(ranges.get(index), FeatureCalculatorSupport.MATH_CONTEXT)
                    .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
            atr = FeatureCalculatorSupport.recursiveValue(atr, definition.numericPolicy());
        }
        return FeatureValue.ready(definition, manifest, atr, computedAt);
    }
}
