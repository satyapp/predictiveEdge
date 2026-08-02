package org.predictiveedge.marketintelligence.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry that resolves only explicit feature definition versions. */
public final class FeatureRegistry {
    private final Map<FeatureDefinitionRef, FeatureDefinition> definitions;

    public FeatureRegistry(Collection<FeatureDefinition> definitions) {
        Objects.requireNonNull(definitions, "Feature definitions are required");
        var indexed = new LinkedHashMap<FeatureDefinitionRef, FeatureDefinition>();
        for (FeatureDefinition definition : definitions) {
            Objects.requireNonNull(definition, "Feature definition cannot be null");
            if (indexed.putIfAbsent(definition.ref(), definition) != null) {
                throw new IllegalArgumentException("Duplicate feature definition: " + definition.ref());
            }
        }
        this.definitions = Map.copyOf(indexed);
    }

    public Optional<FeatureDefinition> find(FeatureDefinitionRef ref) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(ref, "Definition reference is required")));
    }

    public FeatureDefinition require(FeatureDefinitionRef ref) {
        return find(ref).orElseThrow(() -> new IllegalArgumentException("Unknown feature definition: " + ref));
    }

    public int size() {
        return definitions.size();
    }
}
