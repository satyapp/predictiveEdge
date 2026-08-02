package org.predictiveedge.platform.eventing.contract;

/** Governs how an event may be stored, observed, and shared. */
public enum DataClassification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED
}
