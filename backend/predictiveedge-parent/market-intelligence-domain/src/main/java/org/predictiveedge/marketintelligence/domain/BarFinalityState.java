package org.predictiveedge.marketintelligence.domain;

/** Lifecycle state of an immutable market bar revision. */
public enum BarFinalityState {
    PROVISIONAL,
    FINAL,
    CORRECTED,
    INVALID
}
