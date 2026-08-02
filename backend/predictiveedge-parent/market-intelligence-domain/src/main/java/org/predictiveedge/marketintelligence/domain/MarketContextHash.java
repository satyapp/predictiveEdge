package org.predictiveedge.marketintelligence.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class MarketContextHash {
    private MarketContextHash() {
    }

    static ContentHash lineageHash(List<FeatureValue> features) {
        return digest(hasher -> features.stream().map(FeatureValue::inputManifestHash).distinct()
                .sorted(Comparator.comparing(ContentHash::value)).forEach(hash -> add(hasher, hash.value())));
    }

    static ContentHash semanticHash(MarketContextKey key, EvaluationCutoff cutoff, Instant observedThrough,
            Instant decisionReadyAt, Instant expiresAt, MarketRegime regime,
            Map<EvidenceDimension, DimensionAssessment> dimensions, int confidence, ConfidenceBand band,
            List<FeatureValue> features, List<MarketEvidence> evidence, QualityAssessment quality,
            String fusionPolicyVersion, String contextPolicyVersion, ContentHash lineageHash) {
        return digest(digest -> {
            add(digest, key.scopeType().name()); add(digest, key.scopeId()); add(digest, key.horizon());
            add(digest, cutoff.analysisCutoff().toString()); add(digest, cutoff.knowledgeCutoff().toString());
            add(digest, observedThrough.toString()); add(digest, decisionReadyAt.toString()); add(digest, expiresAt.toString());
            add(digest, regime.name()); add(digest, Integer.toString(confidence)); add(digest, band.name());
            for (EvidenceDimension dimension : EvidenceDimension.values()) {
                var value = dimensions.get(dimension);
                add(digest, dimension.name()); add(digest, value.state().name());
                add(digest, Integer.toString(value.confidence())); add(digest, Boolean.toString(value.conflict()));
                value.selectedEvidence().forEach(hash -> add(digest, hash.value()));
            }
            features.stream().sorted(Comparator.comparing(value -> value.definitionRef().featureId().value()))
                    .forEach(value -> {
                        add(digest, value.definitionRef().featureId().value()); add(digest, value.definitionRef().version());
                        add(digest, value.value().stripTrailingZeros().toPlainString()); add(digest, value.unit().name());
                        add(digest, value.valueTime().toString()); add(digest, value.availableAt().toString());
                        add(digest, value.inputManifestHash().value());
                    });
            evidence.stream().map(MarketEvidence::contentHash).sorted(Comparator.comparing(ContentHash::value))
                    .forEach(hash -> add(digest, hash.value()));
            add(digest, quality.contentHash().value()); add(digest, fusionPolicyVersion); add(digest, contextPolicyVersion);
            add(digest, lineageHash.value());
        });
    }

    private static ContentHash digest(java.util.function.Consumer<MessageDigest> values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256"); values.accept(digest);
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array()); digest.update(bytes);
    }
}
