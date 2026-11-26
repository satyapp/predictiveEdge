package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.predictiveedge.chart.domain.PreOpenAuctionDetail;

/**
 * Every screener row returned by a search in the PRE_OPEN category. Each NSE pre-open CSV column is a
 * typed column; the symbol is the inherited key. Rows without a CSV detail leave the columns null.
 */
@Entity
@Table(name = "search_result_pre_open", schema = "market_intelligence")
public class SearchResultPreOpen extends SearchResultStockRow {
    @Column(name = "prev_close")
    private BigDecimal prevClose;
    @Column(name = "best_bid_price")
    private BigDecimal bestBidPrice;
    @Column(name = "best_bid_qty")
    private Long bestBidQty;
    @Column(name = "best_ask_price")
    private BigDecimal bestAskPrice;
    @Column(name = "best_ask_qty")
    private Long bestAskQty;
    @Column(name = "indicative_equilibrium_price")
    private BigDecimal indicativeEquilibriumPrice;
    @Column(name = "indicative_equilibrium_qty")
    private Long indicativeEquilibriumQty;
    @Column(name = "price_change")
    private BigDecimal priceChange;
    @Column(name = "percent_change")
    private BigDecimal percentChange;
    @Column(name = "final_price")
    private BigDecimal finalPrice;
    @Column(name = "final_volume")
    private Long finalVolume;
    @Column(name = "traded_value")
    private BigDecimal tradedValue;
    @Column(name = "ffm_cap")
    private BigDecimal ffmCap;
    @Column(name = "indicative_imbalance_qty_at_equilibrium")
    private Long indicativeImbalanceQtyAtEquilibrium;
    @Column(name = "indicative_imbalance_qty_at_market_order")
    private Long indicativeImbalanceQtyAtMarketOrder;
    @Column(name = "week_52_high")
    private BigDecimal week52High;
    @Column(name = "week_52_low")
    private BigDecimal week52Low;

    protected SearchResultPreOpen() { }

    /** The CSV columns of this row. Rows stored before V020 have every column null. */
    public PreOpenAuctionDetail detail() {
        return new PreOpenAuctionDetail(prevClose, bestBidPrice, bestBidQty, bestAskPrice, bestAskQty,
                indicativeEquilibriumPrice, indicativeEquilibriumQty, priceChange, percentChange, finalPrice,
                finalVolume, tradedValue, ffmCap, indicativeImbalanceQtyAtEquilibrium,
                indicativeImbalanceQtyAtMarketOrder, week52High, week52Low);
    }

    public SearchResultPreOpen(SearchStockKey key, int screenRank, String brokerRow, Instant createdAt,
            PreOpenAuctionDetail detail) {
        super(key, screenRank, brokerRow, createdAt);
        if (detail == null) return;
        this.prevClose = detail.previousClose();
        this.bestBidPrice = detail.bestBidPrice();
        this.bestBidQty = detail.bestBidQuantity();
        this.bestAskPrice = detail.bestAskPrice();
        this.bestAskQty = detail.bestAskQuantity();
        this.indicativeEquilibriumPrice = detail.indicativeEquilibriumPrice();
        this.indicativeEquilibriumQty = detail.indicativeEquilibriumQuantity();
        this.priceChange = detail.priceChange();
        this.percentChange = detail.percentChange();
        this.finalPrice = detail.finalPrice();
        this.finalVolume = detail.finalVolume();
        this.tradedValue = detail.tradedValue();
        this.ffmCap = detail.ffmCap();
        this.indicativeImbalanceQtyAtEquilibrium = detail.indicativeImbalanceQuantityAtEquilibriumPrice();
        this.indicativeImbalanceQtyAtMarketOrder = detail.indicativeImbalanceQuantityAtMarketOrder();
        this.week52High = detail.week52High();
        this.week52Low = detail.week52Low();
    }
}
