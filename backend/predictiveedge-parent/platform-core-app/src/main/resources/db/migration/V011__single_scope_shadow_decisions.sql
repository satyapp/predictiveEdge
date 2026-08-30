create schema if not exists decision;

create table decision.shadow_scope (
    singleton_key smallint primary key check (singleton_key = 1),
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(128) not null,
    mode varchar(16) not null check (mode = 'SHADOW'),
    configured_at timestamptz not null,
    unique (user_id, venue, instrument_id)
);

create table decision.shadow_decision_case (
    case_id varchar(128) primary key,
    bundle_id varchar(128) not null unique,
    recommendation_id varchar(128) unique,
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(128) not null,
    status varchar(32) not null check (status in ('BLOCKED_INPUT','REJECTED_POLICY','RECORDED')),
    action varchar(32) check (action in ('BUY','SELL','WAIT','NO_TRADE','INSUFFICIENT_EVIDENCE')),
    evaluated_at timestamptz not null,
    manifest_hash char(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    input_bundle_json jsonb not null,
    recommendation_json jsonb,
    policy_reasons_json jsonb not null,
    recorded_at timestamptz not null,
    foreign key (user_id, venue, instrument_id)
      references decision.shadow_scope (user_id, venue, instrument_id),
    check ((status = 'BLOCKED_INPUT' and recommendation_id is null and recommendation_json is null and action is null)
        or (status <> 'BLOCKED_INPUT' and recommendation_id is not null and recommendation_json is not null and action is not null))
);

create table decision.shadow_evidence_batch (
    batch_id varchar(128) primary key,
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(128) not null,
    captured_at timestamptz not null,
    valid_until timestamptz not null,
    analysis_cutoff timestamptz not null,
    knowledge_cutoff timestamptz not null,
    manifest_hash char(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    batch_json jsonb not null,
    foreign key (user_id, venue, instrument_id)
      references decision.shadow_scope (user_id, venue, instrument_id),
    check (analysis_cutoff <= knowledge_cutoff),
    check (knowledge_cutoff <= captured_at),
    check (captured_at < valid_until)
);

create table decision.shadow_model_outcome (
    recommendation_id varchar(128) primary key
      references decision.shadow_decision_case (recommendation_id),
    outcome varchar(8) not null check (outcome in ('WIN','LOSS')),
    net_return_after_costs numeric(20,8) not null,
    resolved_at timestamptz not null,
    contract_json jsonb not null,
    outcome_json jsonb not null,
    outcome_definition_version varchar(64) not null
);

create index idx_shadow_case_scope_time
    on decision.shadow_decision_case (user_id, venue, instrument_id, recorded_at desc);
create index idx_shadow_evidence_causal_read
    on decision.shadow_evidence_batch (user_id, venue, instrument_id, captured_at desc, valid_until);

comment on table decision.shadow_scope is
  'Singleton database guard: the personal MVP permits exactly one user, one equity and SHADOW mode only.';
comment on table decision.shadow_decision_case is
  'Append-only AI input, response and policy record. No broker execution state is stored here.';
comment on table decision.shadow_evidence_batch is
  'Append-only causal handoff containing all twelve factual resources, manifest and execution context.';
comment on table decision.shadow_model_outcome is
  'One immutable strict WIN/LOSS result per actionable AI recommendation.';
