package org.predictiveedge.platform.eventing.contract;

/** Identifies the exact immutable context version used by a decision. */
public record ContextReference(String contextType, String contextId, long version, String contentHash) {
    public ContextReference {
        contextType = required(contextType, "Context type");
        contextId = required(contextId, "Context id");
        contentHash = required(contentHash, "Context content hash");
        if (version < 1) {
            throw new IllegalArgumentException("Context version must be positive");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
