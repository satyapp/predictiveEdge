-- Every NSE pre-open CSV column is stored as a typed column of search_result_pre_open.
-- SYMBOL is the existing symbol column. Columns are nullable: rows from earlier runs have no detail,
-- and the exchange reports some columns as "-" when they are unavailable.
alter table market_intelligence.search_result_pre_open
    add column prev_close numeric(18,4),                              -- PREV. CLOSE
    add column best_bid_price numeric(18,4),                          -- BEST BID PRICE
    add column best_bid_qty bigint,                                   -- BEST BID QTY
    add column best_ask_price numeric(18,4),                          -- BEST ASK PRICE
    add column best_ask_qty bigint,                                   -- BEST ASK QTY
    add column indicative_equilibrium_price numeric(18,4),            -- INDICATIVE EQUILIBRIUM PRICE
    add column indicative_equilibrium_qty bigint,                     -- INDICATIVE EQUILIBRIUM QUANTITY
    add column price_change numeric(18,4),                            -- CHANGE
    add column percent_change numeric(12,4),                          -- %CHANGE
    add column final_price numeric(18,4),                             -- FINAL PRICE
    add column final_volume bigint,                                   -- FINAL VOLUME
    add column traded_value numeric(24,4),                            -- VALUE
    add column ffm_cap numeric(24,4),                                 -- FFM CAP
    add column indicative_imbalance_qty_at_equilibrium bigint,        -- INDICATIVE IMBALANCE QUANTITY AT EQUILIBRIUM PRICE
    add column indicative_imbalance_qty_at_market_order bigint,       -- INDICATIVE IMBALANCE QUANTITY AT MARKET ORDER
    add column week_52_high numeric(18,4),                            -- 52 WEEK HIGH
    add column week_52_low numeric(18,4);                             -- 52 WEEK LOW
