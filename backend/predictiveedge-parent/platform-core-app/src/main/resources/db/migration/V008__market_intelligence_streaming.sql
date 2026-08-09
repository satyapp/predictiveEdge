create schema if not exists market_intelligence;

create table market_intelligence.market_session (
    session_definition_id uuid primary key,
    venue varchar(32) not null,
    trading_date date not null,
    session_code varchar(32) not null,
    bar_anchor timestamptz not null,
    session_end timestamptz not null,
    coverage_start timestamptz not null,
    coverage_end timestamptz not null,
    valid_from timestamptz not null,
    valid_to timestamptz,
    calendar_version varchar(64) not null,
    constraint chk_market_session_bounds check (
      bar_anchor < session_end and coverage_start < coverage_end
      and (valid_to is null or valid_from < valid_to)),
    unique (venue,trading_date,session_code,calendar_version)
);

create index idx_market_session_effective_lookup
    on market_intelligence.market_session (venue,coverage_start,coverage_end,valid_from);

create table market_intelligence.market_session_phase (
    session_definition_id uuid not null references market_intelligence.market_session(session_definition_id) on delete cascade,
    phase varchar(32) not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    primary key (session_definition_id,starts_at),
    constraint chk_market_session_phase_bounds check (starts_at < ends_at)
);

create table market_intelligence.market_bar_revision (
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_account_id varchar(128) not null,
    subject_type varchar(32) not null,
    subject_id varchar(160) not null,
    venue varchar(32) not null,
    trading_date date not null,
    session_code varchar(32) not null,
    timeframe varchar(32) not null,
    interval_start timestamptz not null,
    interval_end timestamptz not null,
    is_truncated boolean not null,
    revision bigint not null,
    open_price numeric(20,8) not null,
    high_price numeric(20,8) not null,
    low_price numeric(20,8) not null,
    close_price numeric(20,8) not null,
    volume bigint not null,
    observed_through timestamptz not null,
    finality_state varchar(32) not null,
    available_at timestamptz not null,
    correction_reason varchar(128),
    input_manifest_hash char(64) not null,
    aggregation_policy_version varchar(64) not null,
    finality_policy_version varchar(64) not null,
    primary key (user_id,broker_account_id,subject_type,subject_id,venue,trading_date,
                 session_code,timeframe,interval_start,revision),
    constraint chk_market_bar_interval check (interval_start < interval_end),
    constraint chk_market_bar_revision check (revision > 0),
    constraint chk_market_bar_volume check (volume >= 0)
);

create index idx_market_bar_point_in_time
    on market_intelligence.market_bar_revision
       (subject_type,subject_id,timeframe,interval_end,available_at,revision desc);

create table market_intelligence.market_tick_rejection (
    rejection_id uuid primary key,
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_account_id varchar(128) not null,
    venue varchar(32) not null,
    symbol varchar(128) not null,
    provider_instrument_id varchar(64) not null,
    last_price numeric(20,8) not null,
    cumulative_volume bigint,
    exchange_timestamp timestamptz not null,
    received_at timestamptz not null,
    rejection_reason varchar(64) not null,
    detail varchar(512) not null
);

create index idx_market_tick_rejection_account_time
    on market_intelligence.market_tick_rejection (user_id,broker_account_id,received_at desc);
