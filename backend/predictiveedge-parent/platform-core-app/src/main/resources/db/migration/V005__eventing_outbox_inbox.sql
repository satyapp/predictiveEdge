create schema if not exists eventing;

create table eventing.outbox_event (
    outbox_id uuid primary key,
    event_id uuid not null unique,
    event_type varchar(160) not null,
    aggregate_type varchar(120) not null,
    aggregate_id varchar(256) not null,
    aggregate_version bigint not null check (aggregate_version > 0),
    partition_key varchar(512) not null,
    envelope_json jsonb not null,
    payload_hash char(64) not null,
    schema_version varchar(32) not null,
    created_at timestamptz not null,
    publish_state varchar(16) not null check (publish_state in ('PENDING','CLAIMED','FAILED','PUBLISHED')),
    next_attempt_at timestamptz not null,
    lease_id uuid,
    lease_expires_at timestamptz,
    published_at timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    last_failure varchar(256),
    broker_topic varchar(249),
    broker_partition integer,
    broker_offset bigint,
    check ((lease_id is null) = (lease_expires_at is null)),
    check (broker_partition is null or broker_partition >= 0),
    check (broker_offset is null or broker_offset >= 0)
);

create index idx_eventing_outbox_dispatch
    on eventing.outbox_event (next_attempt_at, created_at)
    where publish_state in ('PENDING','FAILED','CLAIMED');

create table eventing.inbox_event (
    consumer_name varchar(160) not null,
    event_id uuid not null,
    event_type varchar(160) not null,
    aggregate_id varchar(256) not null,
    aggregate_version bigint not null check (aggregate_version > 0),
    topic varchar(249) not null,
    partition_id integer not null check (partition_id >= 0),
    broker_offset bigint not null check (broker_offset >= 0),
    received_at timestamptz not null,
    processed_at timestamptz,
    processing_outcome varchar(16) not null check (processing_outcome in ('PROCESSING','PROCESSED')),
    primary key (consumer_name, event_id),
    unique (consumer_name, topic, partition_id, broker_offset),
    check ((processing_outcome = 'PROCESSING' and processed_at is null)
        or (processing_outcome = 'PROCESSED' and processed_at is not null))
);

create index idx_eventing_inbox_aggregate
    on eventing.inbox_event (consumer_name, aggregate_id, aggregate_version);
