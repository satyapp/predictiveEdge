package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** Immutable, final, lineage-bearing result of one exact feature definition. */
public record FeatureValue(
        FeatureDefinitionRef definitionRef,
        ObservationSubject subject,
        BarTimeframe timeframe,
        BigDecimal value,
        FeatureUnit unit,
        Instant valueTime,
        Instant observedFrom,
        Instant observedThrough,
        Instant availableAt,
        BarFinalityState finality,
        FeatureReadiness readiness,
        SortedMap<String, String> parameters,
        String codeVersion,
        ContentHash inputManifestHash) {

    public FeatureValue {
        Objects.requireNonNull(definitionRef, "Feature definition reference is required");
        Objects.requireNonNull(subject, "Feature subject is required");
        Objects.requireNonNull(timeframe, "Feature timeframe is required");
        Objects.requireNonNull(value, "Feature value is required");
        Objects.requireNonNull(unit, "Feature unit is required");
        Objects.requireNonNull(valueTime, "Feature value time is required");
        Objects.requireNonNull(observedFrom, "Observed-from time is required");
        Objects.requireNonNull(observedThrough, "Observed-through time is required");
        Objects.requireNonNull(availableAt, "Feature availability time is required");
        Objects.requireNonNull(finality, "Feature finality is required");
        if (finality != BarFinalityState.FINAL && finality != BarFinalityState.CORRECTED) {
            throw new IllegalArgumentException("A feature value must be final or corrected");
        }
        if (readiness != FeatureReadiness.READY) {
            throw new IllegalArgumentException("Only a ready calculation can produce a feature value");
        }
        parameters = java.util.Collections.unmodifiableSortedMap(
                new TreeMap<>(Objects.requireNonNull(parameters, "Feature parameters are required")));
        if (codeVersion == null || codeVersion.isBlank()) {
            throw new IllegalArgumentException("Feature code version is required");
        }
        codeVersion = codeVersion.trim();
        Objects.requireNonNull(inputManifestHash, "Feature input manifest hash is required");
        if (observedFrom.isAfter(observedThrough) || observedThrough.isAfter(valueTime)) {
            throw new IllegalArgumentException("Feature observation times are inconsistent");
        }
        if (availableAt.isBefore(valueTime)) {
            throw new IllegalArgumentException("Feature availability cannot precede value time");
        }
    }

    /** Applies the definition's final rounding policy after the caller computes the raw formula result. */
    public static FeatureValue ready(
            FeatureDefinition definition,
            FeatureInputManifest manifest,
            BigDecimal rawValue,
            Instant computedAt) {
        Objects.requireNonNull(definition, "Feature definition is required");
        Objects.requireNonNull(manifest, "Feature input manifest is required");
        Objects.requireNonNull(computedAt, "Feature computation time is required");
        var assessment = FeatureReadinessEvaluator.assess(definition, manifest);
        if (assessment.readiness() != FeatureReadiness.READY) {
            throw new IllegalStateException("Feature is not ready: " + assessment.readiness());
        }

        List<MarketBarRevision> inputs = manifest.bars();
        if (definition.inputRequirement().resetsAtSessionBoundary()) {
            var currentSession = inputs.getLast().key().sessionId();
            inputs = inputs.stream().filter(bar -> bar.key().sessionId().equals(currentSession)).toList();
        }
        var first = inputs.getFirst();
        var last = inputs.getLast();
        var earliestAvailability = last.key().interval().endsAt().plus(definition.causalDelay());
        for (MarketBarRevision input : inputs) {
            if (input.availableAt().isAfter(earliestAvailability)) {
                earliestAvailability = input.availableAt();
            }
        }
        if (computedAt.isBefore(earliestAvailability)) {
            throw new IllegalArgumentException("Feature availability precedes its causal inputs or delay");
        }
        var finality = inputs.stream().anyMatch(input -> input.finalityState() == BarFinalityState.CORRECTED)
                ? BarFinalityState.CORRECTED
                : BarFinalityState.FINAL;
        return new FeatureValue(definition.ref(), manifest.subject(), manifest.timeframe(),
                definition.numericPolicy().round(rawValue), definition.outputUnit(),
                last.key().interval().endsAt(), first.key().interval().startsAt(),
                last.observedThrough(), computedAt, finality, FeatureReadiness.READY,
                definition.parameters(), definition.codeVersion(), manifest.contentHash());
    }
}
