package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;

/** Exact-version deterministic feature implementation. */
public interface FeatureCalculator {
    FeatureDefinitionRef definitionRef();

    FeatureValue calculate(FeatureDefinition definition, FeatureInputManifest manifest, Instant computedAt);
}
