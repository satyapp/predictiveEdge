package org.predictiveedge.marketintelligence.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** The exact, ordered observation revisions visible to one analytical run. */
public record PointInTimeInputManifest(
        EvaluationCutoff cutoff,
        List<CanonicalObservationRevision> observations,
        ContentHash contentHash) {

    public PointInTimeInputManifest {
        Objects.requireNonNull(cutoff, "Evaluation cutoff is required");
        observations = List.copyOf(Objects.requireNonNull(observations, "Observations are required"));
        if (observations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Manifest observations cannot contain null");
        }
        Objects.requireNonNull(contentHash, "Content hash is required");
        if (!contentHash.equals(hash(cutoff, observations))) {
            throw new IllegalArgumentException("Manifest content hash does not match its contents");
        }
    }

    static PointInTimeInputManifest create(
            EvaluationCutoff cutoff,
            List<CanonicalObservationRevision> observations) {
        return new PointInTimeInputManifest(cutoff, observations, hash(cutoff, observations));
    }

    private static ContentHash hash(
            EvaluationCutoff cutoff,
            List<CanonicalObservationRevision> observations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, cutoff.analysisCutoff().toString());
            add(digest, cutoff.knowledgeCutoff().toString());
            for (CanonicalObservationRevision observation : observations) {
                add(digest, observation.observationId().toString());
                add(digest, Integer.toString(observation.revision()));
                add(digest, observation.descriptor().kind().name());
                add(digest, observation.descriptor().schemaId().value());
                add(digest, observation.subject().type().name());
                add(digest, observation.subject().id());
                add(digest, observation.sourceId());
                add(digest, observation.sourceEventId());
                add(digest, observation.eventTime().toString());
                add(digest, observation.sourcePublishedAt() == null
                        ? ""
                        : observation.sourcePublishedAt().toString());
                add(digest, observation.receivedAt().toString());
                add(digest, observation.usableAt().toString());
                add(digest, observation.rawPayloadHash().value());
            }
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
