-- FYERS Automate review loop (ADR-0005). Review-only: none of these tables represents a broker order.
create schema if not exists automate;

create table automate.webhook (
    webhook_id uuid primary key,
    user_id uuid not null,
    label varchar(120) not null,
    encrypted_url text not null,
    url_fingerprint char(64) not null,
    masked_url varchar(255) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    last_delivered_at timestamptz,
    constraint uq_automate_webhook_url unique (user_id, url_fingerprint)
);

create table automate.recommendation_review (
    review_id uuid primary key,
    user_id uuid not null,
    exchange varchar(8) not null,
    symbol varchar(40) not null,
    source_ref varchar(255) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_automate_review_status check (status in ('STAGE_ONE_CREATED','STAGE_ONE_SENT','AWAITING_FIA_REVIEW',
        'FIA_REVIEW_RECORDED','FINAL_READY','FINAL_SENT','CLOSED'))
);
create index idx_automate_review_user_history on automate.recommendation_review (user_id, created_at desc);
create index idx_automate_review_user_symbol on automate.recommendation_review (user_id, symbol, created_at desc);

-- Every stage is an immutable version: PredictiveEdge stage one, FIA review, final (AI + FIA), Guardian revisions.
create table automate.recommendation_version (
    version_id uuid primary key,
    review_id uuid not null references automate.recommendation_review(review_id) on delete cascade,
    stage varchar(32) not null,
    action varchar(16) not null,
    entry_low numeric(18,4),
    entry_high numeric(18,4),
    stop_loss numeric(18,4),
    target_one numeric(18,4),
    target_two numeric(18,4),
    quantity integer,
    confidence integer,
    rationale text not null,
    source varchar(255) not null,
    created_at timestamptz not null,
    constraint chk_automate_version_stage check (stage in ('PREDICTIVEEDGE_STAGE_ONE','FIA_REVIEW','FINAL_AI_FIA','GUARDIAN_REVISION')),
    constraint chk_automate_version_action check (action in ('BUY','SELL','WAIT','NO_TRADE')),
    constraint chk_automate_version_confidence check (confidence is null or confidence between 0 and 100)
);
create index idx_automate_version_review on automate.recommendation_version (review_id, created_at);

create table automate.webhook_delivery (
    delivery_id uuid primary key,
    review_id uuid references automate.recommendation_review(review_id) on delete cascade,
    webhook_id uuid references automate.webhook(webhook_id) on delete set null,
    stage varchar(32),
    sent_at timestamptz not null,
    http_status integer,
    succeeded boolean not null,
    error text,
    payload_json jsonb not null
);
create index idx_automate_delivery_review on automate.webhook_delivery (review_id, sent_at);

-- The trade the trader actually took in FYERS. Feeds future reconciliation as realized track record.
create table automate.trade_outcome (
    review_id uuid primary key references automate.recommendation_review(review_id) on delete cascade,
    direction varchar(8) not null,
    quantity numeric(18,4) not null,
    entry_price numeric(18,4) not null,
    exit_price numeric(18,4) not null,
    entered_at timestamptz not null,
    exited_at timestamptz not null,
    charges numeric(18,4) not null default 0,
    realized_pnl numeric(18,2) not null,
    followed_stage varchar(32) not null,
    monitoring_case_id uuid,
    notes text not null default '',
    recorded_at timestamptz not null,
    constraint chk_automate_outcome_direction check (direction in ('BUY','SELL')),
    constraint chk_automate_outcome_prices check (quantity > 0 and entry_price > 0 and exit_price > 0 and charges >= 0),
    constraint chk_automate_outcome_times check (exited_at >= entered_at)
);
