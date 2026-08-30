create table decision.ai_resource_payload (
    user_id uuid not null,
    venue varchar(32) not null,
    instrument_id varchar(160) not null,
    resource_type varchar(32) not null,
    payload_ref varchar(512) not null,
    evidence_hash char(64) not null,
    available_at timestamptz not null,
    payload_json jsonb not null,
    primary key (user_id, venue, instrument_id, resource_type, payload_ref, evidence_hash),
    constraint chk_ai_resource_payload_hash check (evidence_hash ~ '^[0-9a-f]{64}$')
);

create index idx_ai_resource_payload_exact_lookup
    on decision.ai_resource_payload
       (user_id, venue, instrument_id, resource_type, payload_ref, evidence_hash, available_at);
