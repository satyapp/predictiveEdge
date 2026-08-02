package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Donchian boundary over preceding completed bars, explicitly excluding the current signal bar. */
public final class PriorBarDonchianCalculator implements FeatureCalculator {
    private final FeatureDefinitionRef definitionRef; private final int period; private final DonchianComponent component;

    public PriorBarDonchianCalculator(FeatureDefinitionRef definitionRef, int period, DonchianComponent component) {
        this.definitionRef = Objects.requireNonNull(definitionRef); this.component = Objects.requireNonNull(component);
        if (period < 2) throw new IllegalArgumentException("Donchian period must be at least two"); this.period = period;
    }

    @Override public FeatureDefinitionRef definitionRef() { return definitionRef; }

    @Override
    public FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt) {
        FeatureCalculatorSupport.validate(definition, definitionRef, "period", period);
        if (manifest.bars().size() < period + 1) throw new IllegalStateException("Donchian requires prior bars plus a signal bar");
        var priorBars = manifest.bars().subList(manifest.bars().size() - period - 1, manifest.bars().size() - 1);
        BigDecimal result = component == DonchianComponent.UPPER
                ? priorBars.stream().map(bar -> bar.values().high()).max(BigDecimal::compareTo).orElseThrow()
                : priorBars.stream().map(bar -> bar.values().low()).min(BigDecimal::compareTo).orElseThrow();
        return FeatureValue.ready(definition, manifest, result, computedAt);
    }
}
