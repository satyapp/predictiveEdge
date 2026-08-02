package org.predictiveedge.marketintelligence.domain;

/** Point at which a formula is permitted to round numeric results. */
public enum RoundingBoundary {
    FINAL_OUTPUT,
    EACH_RECURSIVE_STEP
}
