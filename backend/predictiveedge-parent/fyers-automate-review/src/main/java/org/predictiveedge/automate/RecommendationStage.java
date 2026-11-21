package org.predictiveedge.automate;

/** The versions a reviewed recommendation passes through, in order. */
public enum RecommendationStage {
    /** PredictiveEdge's own first-stage recommendation, before FIA sees it. */
    PREDICTIVEEDGE_STAGE_ONE,
    /** FIA's reasoning and correction, entered by the trader after reviewing in FYERS Automate. */
    FIA_REVIEW,
    /** PredictiveEdge AI reconciliation of stage one and the FIA review. */
    FINAL_AI_FIA,
    /** Advisory revision from Trade Guardian after market conditions change (placeholder). */
    GUARDIAN_REVISION
}
