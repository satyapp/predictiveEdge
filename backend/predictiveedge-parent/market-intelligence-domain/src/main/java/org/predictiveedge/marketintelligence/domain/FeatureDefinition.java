package org.predictiveedge.marketintelligence.domain;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** Complete governed contract for one exact feature formula version. */
public record FeatureDefinition(
        FeatureDefinitionRef ref,
        FeatureFamily family,
        String formula,
        FeatureUnit outputUnit,
        BarInputRequirement inputRequirement,
        SortedMap<String, String> parameters,
        NumericPolicy numericPolicy,
        String initializationPolicy,
        String nullPolicy,
        String corporateActionPolicy,
        Duration causalDelay,
        String codeVersion) {

    public FeatureDefinition {
        Objects.requireNonNull(ref, "Feature definition reference is required");
        Objects.requireNonNull(family, "Feature family is required");
        formula = required(formula, "Formula");
        Objects.requireNonNull(outputUnit, "Output unit is required");
        Objects.requireNonNull(inputRequirement, "Input requirement is required");
        parameters = immutableParameters(parameters);
        Objects.requireNonNull(numericPolicy, "Numeric policy is required");
        initializationPolicy = required(initializationPolicy, "Initialization policy");
        nullPolicy = required(nullPolicy, "Null policy");
        corporateActionPolicy = required(corporateActionPolicy, "Corporate-action policy");
        Objects.requireNonNull(causalDelay, "Causal delay is required");
        if (causalDelay.isNegative()) {
            throw new IllegalArgumentException("Causal delay cannot be negative");
        }
        codeVersion = required(codeVersion, "Code version");
    }

    private static SortedMap<String, String> immutableParameters(Map<String, String> values) {
        Objects.requireNonNull(values, "Feature parameters are required");
        var sorted = new TreeMap<String, String>();
        values.forEach((key, value) -> sorted.put(required(key, "Parameter name"),
                required(value, "Parameter value")));
        return Collections.unmodifiableSortedMap(sorted);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
