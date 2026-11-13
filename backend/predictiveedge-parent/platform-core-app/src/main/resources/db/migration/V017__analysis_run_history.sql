create table chart_intelligence.analysis_run (
    run_id uuid primary key,
    user_id uuid not null,
    category varchar(64) not null,
    result_kind varchar(64) not null,
    file_name varchar(255) not null,
    source_sha256 char(64) not null,
    trading_date date not null,
    candidate_count integer not null,
    stage_one_model varchar(160) not null,
    created_at timestamptz not null,
    result_json jsonb not null,
    constraint chk_analysis_run_candidate_count check (candidate_count >= 0),
    constraint chk_analysis_run_source_hash check (source_sha256 ~ '^[0-9a-f]{64}$')
);

create index idx_analysis_run_user_history
    on chart_intelligence.analysis_run (user_id, created_at desc);
