-- Evidence persisted per search run and per AI-selected stock.
-- One run row per Analyse-with-AI request; one category table per dropdown category (plus pre-open).
-- Retention is intentionally not defined here: no purge job exists yet.
create table chart_intelligence.evidence_search_run (
    search_id uuid primary key,
    user_id uuid not null,
    category varchar(64) not null,
    screen_type varchar(64) not null,
    setup varchar(64) not null,
    trading_date date not null,
    evidence_cutoff timestamptz not null,
    requested_symbols jsonb not null,
    persisted_symbol_count integer not null,
    created_at timestamptz not null,
    constraint chk_evidence_search_run_count check (persisted_symbol_count >= 0),
    constraint chk_evidence_search_run_symbols check (jsonb_typeof(requested_symbols) = 'array')
);

create index idx_evidence_search_run_user
    on chart_intelligence.evidence_search_run (user_id, created_at desc);

create table chart_intelligence.search_evidence_top_gainers (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_top_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_top_gainers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_top_gainers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_top_losers (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_top_losers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_top_losers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_top_losers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_most_active_by_volume (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_most_active_by_volume_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_most_active_by_volume_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_most_active_by_volume_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_most_active_by_value (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_most_active_by_value_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_most_active_by_value_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_most_active_by_value_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_week_52_high (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_week_52_high_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_week_52_high_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_week_52_low (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_week_52_low_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_week_52_low_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_near_week_52_high (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_near_week_52_high_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_near_week_52_high_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_near_week_52_high_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_near_week_52_low (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_near_week_52_low_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_near_week_52_low_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_near_week_52_low_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_intraday_surge (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_intraday_surge_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_intraday_surge_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_intraday_surge_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_intraday_fall (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_intraday_fall_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_intraday_fall_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_intraday_fall_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_gap_up (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_gap_up_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_gap_up_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_gap_up_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_gap_down (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_gap_down_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_gap_down_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_gap_down_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_upper_circuit (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_upper_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_upper_circuit_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_upper_circuit_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_lower_circuit (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_lower_circuit_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_lower_circuit_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_lower_circuit_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_volume_gainers (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_volume_gainers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_volume_gainers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_volume_gainers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_fresh_breakouts (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_fresh_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_fresh_breakouts_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_fresh_breakouts_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_near_breakouts (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_near_breakouts_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_near_breakouts_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_near_breakouts_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_technical_crossovers (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_technical_crossovers_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_technical_crossovers_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_technical_crossovers_modules check (jsonb_typeof(intelligence_modules) = 'array')
);

create table chart_intelligence.search_evidence_pre_open (
    search_id uuid not null references chart_intelligence.evidence_search_run (search_id),
    symbol varchar(32) not null,
    evidence_by_domain jsonb not null,
    intelligence_modules jsonb not null,
    created_at timestamptz not null,
    primary key (search_id, symbol),
    constraint chk_search_evidence_pre_open_symbol check (length(symbol) between 1 and 32),
    constraint chk_search_evidence_pre_open_domains check (jsonb_typeof(evidence_by_domain) = 'object'),
    constraint chk_search_evidence_pre_open_modules check (jsonb_typeof(intelligence_modules) = 'array')
);
