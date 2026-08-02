package org.predictiveedge.marketintelligence.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Two point-in-time bar series aligned one-for-one for relative calculations. */
public record SynchronizedBarManifest(FeatureInputManifest primary, FeatureInputManifest comparison,
                                      ContentHash contentHash) {
    public SynchronizedBarManifest {
        Objects.requireNonNull(primary); Objects.requireNonNull(comparison); Objects.requireNonNull(contentHash);
        if (!primary.cutoff().equals(comparison.cutoff()) || primary.timeframe() != comparison.timeframe()
                || primary.bars().size() != comparison.bars().size())
            throw new IllegalArgumentException("Synchronized series have incompatible cutoffs, timeframes or sizes");
        if (primary.subject().equals(comparison.subject()))
            throw new IllegalArgumentException("Relative series must have different subjects");
        for (int index = 0; index < primary.bars().size(); index++)
            if (!primary.bars().get(index).key().interval().endsAt()
                    .equals(comparison.bars().get(index).key().interval().endsAt()))
                throw new IllegalArgumentException("Synchronized series bar endpoints do not align");
        if (!contentHash.equals(hash(primary, comparison)))
            throw new IllegalArgumentException("Synchronized manifest hash does not match its contents");
    }

    public static SynchronizedBarManifest create(FeatureInputManifest primary, FeatureInputManifest comparison) {
        return new SynchronizedBarManifest(primary, comparison, hash(primary, comparison));
    }

    private static ContentHash hash(FeatureInputManifest primary, FeatureInputManifest comparison) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            add(digest, "PRIMARY"); add(digest, primary.subject().type().name()); add(digest, primary.subject().id());
            add(digest, primary.contentHash().value()); add(digest, "COMPARISON");
            add(digest, comparison.subject().type().name()); add(digest, comparison.subject().id());
            add(digest, comparison.contentHash().value());
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
}
