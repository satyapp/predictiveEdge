package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Documented bar-level session VWAP approximation using typical price and final volume. */
public final class SessionVwapCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef;

    public SessionVwapCalculator(FeatureDefinitionRef definitionRef) {
        this.definitionRef = Objects.requireNonNull(definitionRef);
    }

    @Override public FeatureDefinitionRef definitionRef() { return definitionRef; }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef);
        if (!definition.inputRequirement().resetsAtSessionBoundary())
            throw new IllegalArgumentException("Session VWAP definition must reset at the session boundary");
        if (manifest.bars().isEmpty()) throw new IllegalStateException("Session VWAP requires final bars");
        var sessionId = manifest.bars().getLast().key().sessionId();
        var bars = manifest.bars().stream().filter(bar -> bar.key().sessionId().equals(sessionId)).toList();
        BigDecimal weighted = BigDecimal.ZERO; long totalVolume = 0;
        for (MarketBarRevision bar : bars) {
            var values = bar.values();
            var typical = values.high().add(values.low()).add(values.close())
                    .divide(BigDecimal.valueOf(3), FeatureCalculatorSupport.MATH_CONTEXT);
            weighted = weighted.add(typical.multiply(BigDecimal.valueOf(values.volume()),
                    FeatureCalculatorSupport.MATH_CONTEXT));
            totalVolume = Math.addExact(totalVolume, values.volume());
        }
        if (totalVolume == 0) throw new IllegalStateException("Session VWAP total volume is zero");
        return FeatureValue.ready(definition, manifest,
                weighted.divide(BigDecimal.valueOf(totalVolume), FeatureCalculatorSupport.MATH_CONTEXT), computedAt);
    }
}
