create table decision.risk_snapshot (
    snapshot_id varchar(160) not null,
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(160) not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    available_at timestamptz not null,
    valid_until timestamptz not null,
    evidence_hash char(64) not null,
    snapshot_json jsonb not null,
    primary key (user_id, snapshot_id),
    constraint chk_risk_snapshot_times check (
        analysis_cutoff <= knowledge_cutoff and knowledge_cutoff <= available_at and available_at < valid_until),
    constraint chk_risk_snapshot_hash check (evidence_hash ~ '^[0-9a-f]{64}$')
);

create index idx_risk_snapshot_point_in_time
    on decision.risk_snapshot
       (user_id, venue, instrument_id, analysis_cutoff desc, knowledge_cutoff desc, available_at desc);

create table decision.portfolio_snapshot (
    snapshot_id varchar(160) not null,
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(160) not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    available_at timestamptz not null,
    valid_until timestamptz not null,
    evidence_hash char(64) not null,
    snapshot_json jsonb not null,
    primary key (user_id, snapshot_id),
    constraint chk_portfolio_snapshot_times check (
        analysis_cutoff <= knowledge_cutoff and knowledge_cutoff <= available_at and available_at < valid_until),
    constraint chk_portfolio_snapshot_hash check (evidence_hash ~ '^[0-9a-f]{64}$')
);

create index idx_portfolio_snapshot_point_in_time
    on decision.portfolio_snapshot
       (user_id, venue, instrument_id, analysis_cutoff desc, knowledge_cutoff desc, available_at desc);

create table decision.execution_snapshot (
    snapshot_id varchar(160) not null,
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(160) not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    available_at timestamptz not null,
    valid_until timestamptz not null,
    evidence_hash char(64) not null,
    snapshot_json jsonb not null,
    primary key (user_id, snapshot_id),
    constraint chk_execution_snapshot_times check (
        analysis_cutoff <= knowledge_cutoff and knowledge_cutoff <= available_at and available_at < valid_until),
    constraint chk_execution_snapshot_hash check (evidence_hash ~ '^[0-9a-f]{64}$')
);

create index idx_execution_snapshot_point_in_time
    on decision.execution_snapshot
       (user_id, venue, instrument_id, analysis_cutoff desc, knowledge_cutoff desc, available_at desc);
