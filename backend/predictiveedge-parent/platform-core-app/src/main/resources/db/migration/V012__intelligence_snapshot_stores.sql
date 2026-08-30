create table market_intelligence.market_context_snapshot (
    user_id uuid not null,
    scope_type varchar(32) not null,
    scope_id varchar(160) not null,
    horizon varchar(64) not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    decision_ready_at timestamptz not null,
    expires_at timestamptz not null,
    semantic_hash char(64) not null,
    snapshot_json jsonb not null,
    primary key (user_id, scope_type, scope_id, horizon, semantic_hash),
    constraint chk_market_context_causal_times check (
        analysis_cutoff <= knowledge_cutoff
        and knowledge_cutoff <= decision_ready_at
        and decision_ready_at < expires_at),
    constraint chk_market_context_semantic_hash check (semantic_hash ~ '^[0-9a-f]{64}$')
);

create index idx_market_context_point_in_time
    on market_intelligence.market_context_snapshot
       (user_id, scope_type, scope_id, horizon,
        analysis_cutoff desc, knowledge_cutoff desc, decision_ready_at desc);

create schema if not exists chart_intelligence;

create table chart_intelligence.chart_snapshot (
    user_id uuid not null,
    snapshot_id varchar(160) not null,
    venue varchar(32) not null,
    instrument_id varchar(160) not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    available_at timestamptz not null,
    valid_until timestamptz not null,
    input_manifest_hash char(64) not null,
    snapshot_json jsonb not null,
    primary key (user_id, snapshot_id),
    constraint chk_chart_snapshot_causal_times check (
        analysis_cutoff <= knowledge_cutoff
        and knowledge_cutoff <= available_at
        and available_at < valid_until),
    constraint chk_chart_snapshot_manifest_hash check (input_manifest_hash ~ '^[0-9a-f]{64}$')
);

create index idx_chart_snapshot_point_in_time
    on chart_intelligence.chart_snapshot
       (user_id, venue, instrument_id,
        analysis_cutoff desc, knowledge_cutoff desc, available_at desc);
