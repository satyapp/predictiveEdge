package org.predictiveedge.chart.domain;

import java.math.BigDecimal;

/**
 * Every NSE pre-open CSV column except SYMBOL, as parsed. Some values repeat fields of
 * {@link PreOpenSnapshot} so that one record holds the whole CSV row. A null value means the column was
 * missing from the CSV or the exchange reported it as "-".
 */
public record PreOpenAuctionDetail(
        BigDecimal previousClose,
        BigDecimal bestBidPrice,
        Long bestBidQuantity,
        BigDecimal bestAskPrice,
        Long bestAskQuantity,
        BigDecimal indicativeEquilibriumPrice,
        Long indicativeEquilibriumQuantity,
        BigDecimal priceChange,
        BigDecimal percentChange,
        BigDecimal finalPrice,
        Long finalVolume,
        BigDecimal tradedValue,
        BigDecimal ffmCap,
        Long indicativeImbalanceQuantityAtEquilibriumPrice,
        Long indicativeImbalanceQuantityAtMarketOrder,
        BigDecimal week52High,
        BigDecimal week52Low) {
}
