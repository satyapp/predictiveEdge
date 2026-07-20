package org.predictiveedge.broker.domain;

import java.util.Locale;

public record BrokerId(String value) {
    public BrokerId {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9-]{1,31}")) {
            throw new IllegalArgumentException("Broker id must contain 2-32 letters, digits, or hyphens");
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}
