package org.predictiveedge.decision.domain;

/** A veto is authoritative and cannot be overridden by confidence from another module. */
public enum GateDisposition {
    PASS,
    VETO,
    NOT_APPLICABLE
}
