package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable metadata for one version of a canonical market observation. */
public record CanonicalObservationRevision(
        UUID observationId,
        int revision,
        ObservationDescriptor descriptor,
        ObservationSubject subject,
        String sourceId,
        String sourceEventId,
        Instant eventTime,
        Instant sourcePublishedAt,
        Instant receivedAt,
        Instant usableAt,
        ContentHash rawPayloadHash) {

    public CanonicalObservationRevision {
        Objects.requireNonNull(observationId, "Observation id is required");
        if (revision < 1) {
            throw new IllegalArgumentException("Observation revision must be positive");
        }
        Objects.requireNonNull(descriptor, "Observation descriptor is required");
        Objects.requireNonNull(subject, "Observation subject is required");
        sourceId = required(sourceId, "Source id").toUpperCase(Locale.ROOT);
        sourceEventId = required(sourceEventId, "Source event id");
        Objects.requireNonNull(eventTime, "Event time is required");
        Objects.requireNonNull(receivedAt, "Received time is required");
        Objects.requireNonNull(usableAt, "Usable time is required");
        Objects.requireNonNull(rawPayloadHash, "Raw payload hash is required");
        if (receivedAt.isBefore(eventTime)) {
            throw new IllegalArgumentException("Received time cannot precede event time");
        }
        if (sourcePublishedAt != null && sourcePublishedAt.isAfter(receivedAt)) {
            throw new IllegalArgumentException("Source publication time cannot follow platform receipt time");
        }
        if (usableAt.isBefore(receivedAt)) {
            throw new IllegalArgumentException("Usable time cannot precede received time");
        }
    }

    public boolean isEligible(EvaluationCutoff cutoff) {
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        return !eventTime.isAfter(cutoff.analysisCutoff())
                && !usableAt.isAfter(cutoff.knowledgeCutoff());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
