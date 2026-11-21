package org.predictiveedge.automate;

/** Where a recommendation is in the PredictiveEdge → FIA → PredictiveEdge loop. */
public enum ReviewStatus {
    STAGE_ONE_CREATED,
    STAGE_ONE_SENT,
    AWAITING_FIA_REVIEW,
    FIA_REVIEW_RECORDED,
    FINAL_READY,
    FINAL_SENT,
    CLOSED
}
