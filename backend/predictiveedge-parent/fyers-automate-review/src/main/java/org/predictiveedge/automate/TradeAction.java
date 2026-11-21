package org.predictiveedge.automate;

/** Advisory action; none of these values is an order instruction. */
public enum TradeAction {
    BUY, SELL, WAIT, NO_TRADE;

    public boolean isDirectional() { return this == BUY || this == SELL; }
}
