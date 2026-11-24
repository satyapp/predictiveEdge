package org.predictiveedge.intelligence.connector;

public enum EvidenceFetchStatus {
    SUCCESS,
    UNAVAILABLE,
    UNSUPPORTED,
    FAILED,
    /** Not called because an earlier source in the plan already returned usable evidence. */
    SKIPPED
}
