package org.predictiveedge.chart.domain;

/** Advisory candidate direction; final BUY/SELL authority remains with Decision Intelligence. */
public enum IntradaySetupAction {
    BUY_CANDIDATE,
    SELL_CANDIDATE,
    WAIT,
    NO_TRADE,
    INSUFFICIENT_EVIDENCE
}
