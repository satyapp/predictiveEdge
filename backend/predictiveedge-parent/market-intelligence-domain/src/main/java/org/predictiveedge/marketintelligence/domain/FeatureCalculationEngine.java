package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact-version dispatch for deterministic feature calculators. */
public final class FeatureCalculationEngine {
    private final Map<FeatureDefinitionRef, FeatureCalculator> calculators;

    public FeatureCalculationEngine(Collection<FeatureCalculator> calculators) {
        Objects.requireNonNull(calculators, "Feature calculators are required");
        var indexed = new LinkedHashMap<FeatureDefinitionRef, FeatureCalculator>();
        for (FeatureCalculator calculator : calculators) {
            Objects.requireNonNull(calculator, "Feature calculator cannot be null");
            if (indexed.putIfAbsent(calculator.definitionRef(), calculator) != null) {
                throw new IllegalArgumentException("Duplicate feature calculator: " + calculator.definitionRef());
            }
        }
        this.calculators = Map.copyOf(indexed);
    }

    public FeatureValue calculate(
            FeatureDefinition definition,
            FeatureInputManifest manifest,
            Instant computedAt) {
        var calculator = calculators.get(Objects.requireNonNull(definition, "Feature definition is required").ref());
        if (calculator == null) {
            throw new IllegalArgumentException("No calculator for feature definition: " + definition.ref());
        }
        return calculator.calculate(definition, manifest, computedAt);
    }
}
