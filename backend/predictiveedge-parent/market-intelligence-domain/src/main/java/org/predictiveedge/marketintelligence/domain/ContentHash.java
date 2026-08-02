package org.predictiveedge.marketintelligence.domain;

import java.util.Locale;

/** A canonical lowercase SHA-256 digest. */
public record ContentHash(String value) {
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";

    public ContentHash {
        if (value == null || !value.toLowerCase(Locale.ROOT).matches(SHA_256_PATTERN)) {
            throw new IllegalArgumentException("Content hash must be a 64-character SHA-256 digest");
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}
