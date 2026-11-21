/**
 * FYERS Automate review loop.
 *
 * <p>PredictiveEdge sends a recommendation to a trader-owned FYERS Automate webhook, the trader reviews it with
 * FIA inside FYERS and records FIA's correction here, and PredictiveEdge reconciles both into a final
 * recommendation that is sent back. Every payload is marked review-only: nothing in this package places,
 * modifies, or cancels a broker order (ADR-0004, ADR-0005). The trader confirms any live order in FYERS.
 */
package org.predictiveedge.automate;
