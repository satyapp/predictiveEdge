package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Population-standard-deviation Bollinger width as a percentage of the middle SMA. */
public final class BollingerBandWidthCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;
    private final int period;
    private final BigDecimal standardDeviations;

    public BollingerBandWidthCalculator(FeatureDefinitionRef definitionRef, int period, BigDecimal standardDeviations) {
        this.definitionRef = Objects.requireNonNull(definitionRef); this.standardDeviations = Objects.requireNonNull(standardDeviations);
        if (period < 2 || standardDeviations.signum() <= 0) throw new IllegalArgumentException("Bollinger parameters are invalid");
        this.period = period;
    }

    @Override public FeatureDefinitionRef definitionRef() { return definitionRef; }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (!standardDeviations.toPlainString().equals(definition.parameters().get("standardDeviations")))
            throw new IllegalArgumentException("Bollinger deviation parameter does not match calculator binding");
        if (manifest.bars().size() < period) throw new IllegalStateException("Bollinger width has not completed warm-up");
        var closes = manifest.bars().subList(manifest.bars().size() - period, manifest.bars().size()).stream()
                .map(bar -> bar.values().close()).toList();
        BigDecimal mean = closes.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        if (mean.signum() == 0) throw new IllegalStateException("Bollinger middle band is zero");
        BigDecimal variance = closes.stream().map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        BigDecimal standardDeviation = variance.sqrt(FeatureCalculatorSupport.MATH_CONTEXT);
        BigDecimal widthPercent = standardDeviation.multiply(standardDeviations).multiply(BigDecimal.valueOf(2))
                .divide(mean, FeatureCalculatorSupport.MATH_CONTEXT).multiply(BigDecimal.valueOf(100));
        return FeatureValue.ready(definition, manifest, widthPercent, computedAt);
    }
}
