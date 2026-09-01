create table market_intelligence.market_depth_snapshot (
    snapshot_id uuid primary key,
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_account_id varchar(128) not null,
    venue varchar(32) not null,
    symbol varchar(128) not null,
    provider_instrument_id varchar(64) not null,
    exchange_timestamp timestamptz not null,
    received_at timestamptz not null,
    buy_depth_json jsonb not null,
    sell_depth_json jsonb not null,
    evidence_hash char(64) not null,
    constraint chk_market_depth_hash check (evidence_hash ~ '^[0-9a-f]{64}$'),
    unique (user_id, broker_account_id, provider_instrument_id, exchange_timestamp, received_at, evidence_hash)
);

create index idx_market_depth_point_in_time
    on market_intelligence.market_depth_snapshot
       (user_id, broker_account_id, venue, symbol, received_at desc, exchange_timestamp desc);

create schema if not exists broker_evidence;

create table broker_evidence.account_snapshot (
    snapshot_id uuid primary key,
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_account_id varchar(128) not null,
    broker varchar(32) not null,
    observed_at timestamptz not null,
    received_at timestamptz not null,
    funds_json jsonb not null,
    positions_json jsonb not null,
    holdings_json jsonb not null,
    evidence_hash char(64) not null,
    constraint chk_broker_account_snapshot_times check (observed_at <= received_at),
    constraint chk_broker_account_snapshot_hash check (evidence_hash ~ '^[0-9a-f]{64}$'),
    unique (user_id, broker_account_id, observed_at, evidence_hash)
);

create index idx_broker_account_snapshot_point_in_time
    on broker_evidence.account_snapshot (user_id, broker_account_id, received_at desc, observed_at desc);
