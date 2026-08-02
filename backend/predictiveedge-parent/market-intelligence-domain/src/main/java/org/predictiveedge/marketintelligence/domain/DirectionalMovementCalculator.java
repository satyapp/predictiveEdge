package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/** Wilder +DI, -DI and ADX calculation exposed as one governed dependency family. */
public final class DirectionalMovementCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef; private final int period;
    private final DirectionalMovementComponent component;

    public DirectionalMovementCalculator(FeatureDefinitionRef definitionRef, int period,
            DirectionalMovementComponent component) {
        this.definitionRef = Objects.requireNonNull(definitionRef); this.component = Objects.requireNonNull(component);
        if (period < 2) throw new IllegalArgumentException("DMI period must be at least two"); this.period = period;
    }

    @Override public FeatureDefinitionRef definitionRef() { return definitionRef; }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (manifest.bars().size() < period * 2) throw new IllegalStateException("ADX requires two Wilder periods of bars");
        var bars = manifest.bars(); var trueRanges = new ArrayList<BigDecimal>();
        var plusMoves = new ArrayList<BigDecimal>(); var minusMoves = new ArrayList<BigDecimal>();
        for (int index = 1; index < bars.size(); index++) {
            var current = bars.get(index).values(); var prior = bars.get(index - 1).values();
            var upMove = current.high().subtract(prior.high()); var downMove = prior.low().subtract(current.low());
            plusMoves.add(upMove.signum() > 0 && upMove.compareTo(downMove) > 0 ? upMove : BigDecimal.ZERO);
            minusMoves.add(downMove.signum() > 0 && downMove.compareTo(upMove) > 0 ? downMove : BigDecimal.ZERO);
            trueRanges.add(current.high().subtract(current.low()).max(current.high().subtract(prior.close()).abs())
                    .max(current.low().subtract(prior.close()).abs()));
        }
        BigDecimal smoothedTr = sum(trueRanges, 0, period); BigDecimal smoothedPlus = sum(plusMoves, 0, period);
        BigDecimal smoothedMinus = sum(minusMoves, 0, period); var dxValues = new ArrayList<BigDecimal>();
        BigDecimal plusDi = BigDecimal.ZERO; BigDecimal minusDi = BigDecimal.ZERO;
        for (int index = period - 1; index < trueRanges.size(); index++) {
            if (index >= period) {
                smoothedTr = wilderSum(smoothedTr, trueRanges.get(index), period);
                smoothedPlus = wilderSum(smoothedPlus, plusMoves.get(index), period);
                smoothedMinus = wilderSum(smoothedMinus, minusMoves.get(index), period);
            }
            if (smoothedTr.signum() == 0) { plusDi = BigDecimal.ZERO; minusDi = BigDecimal.ZERO; }
            else {
                plusDi = smoothedPlus.multiply(BigDecimal.valueOf(100)).divide(smoothedTr, FeatureCalculatorSupport.MATH_CONTEXT);
                minusDi = smoothedMinus.multiply(BigDecimal.valueOf(100)).divide(smoothedTr, FeatureCalculatorSupport.MATH_CONTEXT);
            }
            var denominator = plusDi.add(minusDi);
            dxValues.add(denominator.signum() == 0 ? BigDecimal.ZERO
                    : plusDi.subtract(minusDi).abs().multiply(BigDecimal.valueOf(100))
                            .divide(denominator, FeatureCalculatorSupport.MATH_CONTEXT));
        }
        BigDecimal adx = sum(dxValues, 0, period).divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        for (int index = period; index < dxValues.size(); index++)
            adx = adx.multiply(BigDecimal.valueOf(period - 1L)).add(dxValues.get(index))
                    .divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT);
        BigDecimal result = switch (component) { case PLUS_DI -> plusDi; case MINUS_DI -> minusDi; case ADX -> adx; };
        return FeatureValue.ready(definition, manifest, result, computedAt);
    }

    private static BigDecimal sum(java.util.List<BigDecimal> values, int start, int count) {
        return values.subList(start, start + count).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal wilderSum(BigDecimal previous, BigDecimal current, int period) {
        return previous.subtract(previous.divide(BigDecimal.valueOf(period), FeatureCalculatorSupport.MATH_CONTEXT)).add(current);
    }
}
