-- Market-intelligence search, selection and intraday-list lineage.
-- Flow: search criteria -> search result and all listed stocks -> user-selected stocks
--       -> evidence and stage 1 AI analysis -> intraday list (STAGE_1) -> stage 2 pass (STAGE_2).
-- Stage markers: search_result is STAGE_1; stage-keyed tables carry STAGE_1 or STAGE_2.
-- Retention is intentionally not defined here: no purge job exists yet.

create schema if not exists market_intelligence;

create table market_intelligence.search_result (
    search_id uuid primary key,
    user_id uuid not null,
    category varchar(64) not null,
    screen_type varchar(64) not null,
    setup varchar(64) not null,
    universe varchar(64) not null,
    timeframe varchar(64) not null,
    trading_date date not null,
    broker_account_id varchar(128) not null,
    criteria_json jsonb not null,
    result_count integer not null,
    stage varchar(16) not null,
    searched_at timestamptz not null,
    constraint chk_search_result_category check (category in ('TOP_GAINERS', 'TOP_LOSERS', 'MOST_ACTIVE_BY_VOLUME', 'MOST_ACTIVE_BY_VALUE', 'WEEK_52_HIGH', 'WEEK_52_LOW', 'NEAR_WEEK_52_HIGH', 'NEAR_WEEK_52_LOW', 'INTRADAY_SURGE', 'INTRADAY_FALL', 'GAP_UP', 'GAP_DOWN', 'UPPER_CIRCUIT', 'LOWER_CIRCUIT', 'VOLUME_GAINERS', 'FRESH_BREAKOUTS', 'NEAR_BREAKOUTS', 'TECHNICAL_CROSSOVERS', 'PRE_OPEN')),
    constraint chk_search_result_stage check (stage = 'STAGE_1'),
    constraint chk_search_result_count check (result_count >= 0),
    constraint chk_search_result_criteria check (jsonb_typeof(criteria_json) = 'object')
);

create index idx_search_result_user
    on market_intelligence.search_result (user_id, searched_at desc);

create table market_intelligence.selected_stock (
    selection_id uuid primary key,
    search_id uuid not null references market_intelligence.search_result (search_id),
    user_id uuid not null,
    symbol varchar(32) not null,
    stage varchar(16) not null,
    selected_at timestamptz not null,
    constraint uq_selected_stock_search_symbol unique (search_id, symbol),
    constraint chk_selected_stock_stage check (stage = 'STAGE_1'),
    constraint chk_selected_stock_symbol check (length(symbol) between 1 and 32)
);

create table market_intelligence.selected_stock_evidence (
    selection_id uuid not null references market_intelligence.selected_stock (selection_id),
    stage varchar(16) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    fetched_at timestamptz not null,
    primary key (selection_id, stage),
    constraint chk_selected_stock_evidence_stage check (stage in ('STAGE_1', 'STAGE_2')),
    constraint chk_selected_stock_evidence_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_selected_stock_evidence_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table market_intelligence.selected_stock_ai_analysis (
    selection_id uuid not null references market_intelligence.selected_stock (selection_id),
    stage varchar(16) not null,
    stance varchar(32) not null,
    thesis text not null,
    confirmation text not null,
    risk text not null,
    model varchar(160) not null,
    analysis_json jsonb not null,
    generated_at timestamptz not null,
    primary key (selection_id, stage),
    constraint chk_selected_stock_ai_stage check (stage in ('STAGE_1', 'STAGE_2')),
    constraint chk_selected_stock_ai_json check (jsonb_typeof(analysis_json) = 'object')
);

create table market_intelligence.intraday_list (
    list_id uuid primary key,
    user_id uuid not null,
    stage varchar(16) not null,
    parent_list_id uuid references market_intelligence.intraday_list (list_id),
    item_count integer not null,
    saved_at timestamptz not null,
    constraint chk_intraday_list_stage check (stage in ('STAGE_1', 'STAGE_2')),
    constraint chk_intraday_list_parent check (
        (stage = 'STAGE_1' and parent_list_id is null)
        or (stage = 'STAGE_2' and parent_list_id is not null)),
    constraint chk_intraday_list_count check (item_count >= 0)
);

create index idx_intraday_list_user
    on market_intelligence.intraday_list (user_id, saved_at desc);

create table market_intelligence.intraday_list_item (
    list_id uuid not null references market_intelligence.intraday_list (list_id),
    selection_id uuid not null references market_intelligence.selected_stock (selection_id),
    stage varchar(16) not null,
    primary key (list_id, selection_id),
    constraint fk_intraday_list_item_analysis foreign key (selection_id, stage)
        references market_intelligence.selected_stock_ai_analysis (selection_id, stage),
    constraint chk_intraday_list_item_stage check (stage in ('STAGE_1', 'STAGE_2'))
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_top_gainers (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_top_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_top_gainers_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_top_gainers_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_top_losers (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_top_losers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_top_losers_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_top_losers_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_most_active_by_volume (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_most_active_by_volume_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_most_active_by_volume_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_most_active_by_volume_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_most_active_by_value (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_most_active_by_value_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_most_active_by_value_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_most_active_by_value_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_week_52_high (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_week_52_high_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_week_52_high_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_week_52_low (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_week_52_low_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_week_52_low_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_near_week_52_high (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_near_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_near_week_52_high_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_near_week_52_high_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_near_week_52_low (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_near_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_near_week_52_low_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_near_week_52_low_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_intraday_surge (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_intraday_surge_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_intraday_surge_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_intraday_surge_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_intraday_fall (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_intraday_fall_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_intraday_fall_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_intraday_fall_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_gap_up (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_gap_up_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_gap_up_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_gap_up_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_gap_down (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_gap_down_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_gap_down_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_gap_down_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_upper_circuit (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_upper_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_upper_circuit_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_upper_circuit_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_lower_circuit (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_lower_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_lower_circuit_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_lower_circuit_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_volume_gainers (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_volume_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_volume_gainers_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_volume_gainers_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_fresh_breakouts (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_fresh_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_fresh_breakouts_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_fresh_breakouts_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_near_breakouts (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_near_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_near_breakouts_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_near_breakouts_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_technical_crossovers (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_technical_crossovers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_technical_crossovers_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_technical_crossovers_row check (jsonb_typeof(broker_row) = 'object')
);

-- Every screener row returned by a search in this category.
create table market_intelligence.search_result_stock_pre_open (
    search_id uuid not null references market_intelligence.search_result (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_stock_pre_open_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_stock_pre_open_rank check (screen_rank >= 1),
    constraint chk_search_result_stock_pre_open_row check (jsonb_typeof(broker_row) = 'object')
);
