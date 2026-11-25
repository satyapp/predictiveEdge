-- Four-level evidence lineage for Analyse-with-AI and Intraday List runs.
--   L1 search result     : search_result_<category>, one row per screener row returned by a search
--   L2 AI analysis       : evidence_selection (AI_ANALYSIS) plus stage_evidence_<category>
--   L3 Intraday List     : evidence_selection (INTRADAY_LIST), parent = its L2 row
--   L4 stage 2 pass      : evidence_selection (STAGE_2_ANALYSIS), parent = its stage 1 L2 row, plus stage_evidence_<category>
-- Retention is intentionally not defined here: no purge job exists yet.

create table evidence_search_run (
    search_id uuid primary key,
    user_id uuid not null,
    category varchar(64) not null,
    screen_type varchar(64) not null,
    setup varchar(64) not null,
    universe varchar(64) not null,
    timeframe varchar(64) not null,
    trading_date date not null,
    broker_account_id varchar(128) not null,
    stage varchar(16) not null,
    result_count integer not null,
    searched_at timestamptz not null,
    constraint chk_evidence_search_run_stage check (stage in ('STAGE_1', 'STAGE_2')),
    constraint chk_evidence_search_run_count check (result_count >= 0)
);

create index idx_evidence_search_run_user
    on evidence_search_run (user_id, searched_at desc);

create table evidence_selection (
    selection_id uuid primary key,
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    selection_level varchar(32) not null,
    stage varchar(16) not null,
    parent_selection_id uuid references evidence_selection (selection_id),
    user_id uuid not null,
    created_at timestamptz not null,
    constraint uq_evidence_selection_key unique (search_id, symbol, selection_level, stage),
    constraint chk_evidence_selection_level check (selection_level in ('AI_ANALYSIS', 'INTRADAY_LIST', 'STAGE_2_ANALYSIS')),
    constraint chk_evidence_selection_stage check (stage in ('STAGE_1', 'STAGE_2')),
    constraint chk_evidence_selection_stage_2 check ((selection_level = 'STAGE_2_ANALYSIS') = (stage = 'STAGE_2')),
    constraint chk_evidence_selection_parent check (
        (selection_level = 'AI_ANALYSIS' and parent_selection_id is null)
        or (selection_level <> 'AI_ANALYSIS' and parent_selection_id is not null)),
    constraint chk_evidence_selection_symbol check (length(symbol) between 1 and 32)
);

create index idx_evidence_selection_parent
    on evidence_selection (parent_selection_id);

-- L1: every screener row returned by a search in this category.
create table search_result_top_gainers (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_top_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_top_gainers_rank check (screen_rank >= 1),
    constraint chk_search_result_top_gainers_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_top_gainers (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_top_gainers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_top_gainers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_top_losers (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_top_losers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_top_losers_rank check (screen_rank >= 1),
    constraint chk_search_result_top_losers_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_top_losers (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_top_losers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_top_losers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_most_active_by_volume (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_most_active_by_volume_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_most_active_by_volume_rank check (screen_rank >= 1),
    constraint chk_search_result_most_active_by_volume_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_most_active_by_volume (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_most_active_by_volume_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_most_active_by_volume_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_most_active_by_value (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_most_active_by_value_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_most_active_by_value_rank check (screen_rank >= 1),
    constraint chk_search_result_most_active_by_value_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_most_active_by_value (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_most_active_by_value_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_most_active_by_value_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_week_52_high (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_week_52_high_rank check (screen_rank >= 1),
    constraint chk_search_result_week_52_high_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_week_52_high (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_week_52_high_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_week_52_high_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_week_52_low (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_week_52_low_rank check (screen_rank >= 1),
    constraint chk_search_result_week_52_low_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_week_52_low (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_week_52_low_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_week_52_low_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_near_week_52_high (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_near_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_near_week_52_high_rank check (screen_rank >= 1),
    constraint chk_search_result_near_week_52_high_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_near_week_52_high (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_near_week_52_high_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_near_week_52_high_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_near_week_52_low (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_near_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_near_week_52_low_rank check (screen_rank >= 1),
    constraint chk_search_result_near_week_52_low_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_near_week_52_low (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_near_week_52_low_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_near_week_52_low_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_intraday_surge (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_intraday_surge_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_intraday_surge_rank check (screen_rank >= 1),
    constraint chk_search_result_intraday_surge_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_intraday_surge (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_intraday_surge_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_intraday_surge_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_intraday_fall (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_intraday_fall_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_intraday_fall_rank check (screen_rank >= 1),
    constraint chk_search_result_intraday_fall_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_intraday_fall (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_intraday_fall_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_intraday_fall_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_gap_up (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_gap_up_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_gap_up_rank check (screen_rank >= 1),
    constraint chk_search_result_gap_up_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_gap_up (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_gap_up_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_gap_up_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_gap_down (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_gap_down_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_gap_down_rank check (screen_rank >= 1),
    constraint chk_search_result_gap_down_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_gap_down (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_gap_down_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_gap_down_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_upper_circuit (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_upper_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_upper_circuit_rank check (screen_rank >= 1),
    constraint chk_search_result_upper_circuit_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_upper_circuit (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_upper_circuit_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_upper_circuit_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_lower_circuit (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_lower_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_lower_circuit_rank check (screen_rank >= 1),
    constraint chk_search_result_lower_circuit_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_lower_circuit (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_lower_circuit_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_lower_circuit_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_volume_gainers (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_volume_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_volume_gainers_rank check (screen_rank >= 1),
    constraint chk_search_result_volume_gainers_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_volume_gainers (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_volume_gainers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_volume_gainers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_fresh_breakouts (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_fresh_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_fresh_breakouts_rank check (screen_rank >= 1),
    constraint chk_search_result_fresh_breakouts_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_fresh_breakouts (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_fresh_breakouts_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_fresh_breakouts_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_near_breakouts (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_near_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_near_breakouts_rank check (screen_rank >= 1),
    constraint chk_search_result_near_breakouts_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_near_breakouts (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_near_breakouts_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_near_breakouts_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_technical_crossovers (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_technical_crossovers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_technical_crossovers_rank check (screen_rank >= 1),
    constraint chk_search_result_technical_crossovers_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_technical_crossovers (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_technical_crossovers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_technical_crossovers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

-- L1: every screener row returned by a search in this category.
create table search_result_pre_open (
    search_id uuid not null references evidence_search_run (search_id),
    symbol varchar(32) not null,
    screen_rank integer not null,
    broker_row jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_result_pre_open_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_result_pre_open_rank check (screen_rank >= 1),
    constraint chk_search_result_pre_open_row check (jsonb_typeof(broker_row) = 'object')
);

-- L2 and L4: evidence for the selection. One row per selection.
create table stage_evidence_pre_open (
    selection_id uuid primary key references evidence_selection (selection_id),
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    constraint chk_stage_evidence_pre_open_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_stage_evidence_pre_open_modules check (jsonb_typeof(intelligence_modules) = 'array')
);
