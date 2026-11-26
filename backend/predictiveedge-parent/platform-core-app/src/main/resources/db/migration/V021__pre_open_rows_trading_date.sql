-- Every pre-open row carries its trading date, copied from the search that holds it, and is indexed with its symbol.
-- The indexed (trading_date, symbol) pair lets a later lookup read the last 90 days of pre-open rows for a symbol
-- without going through search_result.
alter table market_intelligence.search_result_pre_open add column trading_date date;

update market_intelligence.search_result_pre_open p
set trading_date = r.trading_date
from market_intelligence.search_result r
where r.search_id = p.search_id;

alter table market_intelligence.search_result_pre_open alter column trading_date set not null;

create index idx_search_result_pre_open_trading_date_symbol
    on market_intelligence.search_result_pre_open (trading_date, symbol);
