create schema if not exists guardian;

create table guardian.trade_monitoring_case (
    monitoring_case_id uuid primary key,
    trade_id uuid not null unique,
    trader_id uuid not null,
    recommendation_id varchar(256) not null unique,
    approved_trade_plan_ref varchar(512) not null,
    account_ref varchar(256) not null,
    venue varchar(64) not null,
    symbol varchar(128) not null,
    direction varchar(8) not null check (direction in ('LONG','SHORT')),
    monitoring_state varchar(16) not null check (monitoring_state in ('ACTIVE','SUSPENDED','COMPLETED')),
    aggregate_version bigint not null check (aggregate_version > 0),
    snapshot_json jsonb not null,
    registered_at timestamptz not null,
    state_changed_at timestamptz not null,
    check (state_changed_at >= registered_at)
);

create index idx_guardian_case_trader_state
    on guardian.trade_monitoring_case (trader_id, monitoring_state, state_changed_at desc);
