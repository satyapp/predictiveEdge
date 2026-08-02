package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** EMA with an SMA seed and definition-governed recursive rounding. */
public final class ExponentialMovingAverageCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;
    private final int period;

    public ExponentialMovingAverageCalculator(FeatureDefinitionRef definitionRef, int period) {
        this.definitionRef = Objects.requireNonNull(definitionRef, "Feature definition reference is required");
        if (period < 2) {
            throw new IllegalArgumentException("EMA period must be at least two");
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
        if (manifest.bars().size() < period) {
            throw new IllegalStateException("EMA input has not completed its seed period");
        }
        var closes = manifest.bars().stream().map(bar -> bar.values().close()).toList();
        BigDecimal seed = closes.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        BigDecimal alpha = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), FeatureCalculatorSupport.MATH_CONTEXT);
        BigDecimal ema = FeatureCalculatorSupport.recursiveValue(seed, definition.numericPolicy());
        for (int index = period; index < closes.size(); index++) {
            ema = closes.get(index).subtract(ema).multiply(alpha, FeatureCalculatorSupport.MATH_CONTEXT)
                    .add(ema, FeatureCalculatorSupport.MATH_CONTEXT);
            ema = FeatureCalculatorSupport.recursiveValue(ema, definition.numericPolicy());
        }
        return FeatureValue.ready(definition, manifest, ema, computedAt);
    }
}
