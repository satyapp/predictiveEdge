package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One stock on an intraday list. */
@Entity
@Table(name = "intraday_list_item", schema = "market_intelligence")
public class IntradayListItem {
    @EmbeddedId
    private IntradayListItemKey key;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "stage", nullable = false)
    private String stage;

    protected IntradayListItem() { }

    public IntradayListItem(IntradayListItemKey key, String symbol, String stage) {
        this.key = key;
        this.symbol = symbol;
        this.stage = stage;
    }
}
