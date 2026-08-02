package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.math.MathContext;

final class FeatureCalculatorSupport {
    static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    private FeatureCalculatorSupport() {
    }

    static void validate(FeatureDefinition definition, FeatureDefinitionRef expected, String parameter, int value) {
        validate(definition, expected);
        String configured = definition.parameters().get(parameter);
        if (configured == null || Integer.parseInt(configured) != value) {
            throw new IllegalArgumentException("Feature parameter does not match calculator binding: " + parameter);
        }
    }

    static void validate(FeatureDefinition definition, FeatureDefinitionRef expected) {
        if (!definition.ref().equals(expected)) {
            throw new IllegalArgumentException("Feature definition does not match calculator binding");
        }
    }

    static BigDecimal recursiveValue(BigDecimal value, NumericPolicy policy) {
        return policy.roundingBoundary() == RoundingBoundary.EACH_RECURSIVE_STEP
                ? policy.round(value)
                : value;
    }
}
