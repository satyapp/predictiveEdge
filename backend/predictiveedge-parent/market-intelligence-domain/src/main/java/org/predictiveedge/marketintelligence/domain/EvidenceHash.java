package org.predictiveedge.marketintelligence.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class EvidenceHash {
    private EvidenceHash() {
    }

    static ContentHash hash(EvidenceDimension dimension, EvidenceState state, int strength, int uncertainty,
            String dependencyKey, Instant effectiveAt, Instant detectedAt, Instant expiresAt,
            List<FeatureValue> features, String ruleVersion) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            add(digest, dimension.name()); add(digest, state.name()); add(digest, Integer.toString(strength));
            add(digest, Integer.toString(uncertainty)); add(digest, dependencyKey.trim());
            add(digest, effectiveAt.toString()); add(digest, detectedAt.toString()); add(digest, expiresAt.toString());
            features.stream().sorted(Comparator.comparing(value -> value.definitionRef().featureId().value()))
                    .forEach(value -> {
                        add(digest, value.definitionRef().featureId().value());
                        add(digest, value.definitionRef().version());
                        add(digest, value.value().stripTrailingZeros().toPlainString());
                        add(digest, value.inputManifestHash().value());
                    });
            add(digest, ruleVersion.trim());
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
