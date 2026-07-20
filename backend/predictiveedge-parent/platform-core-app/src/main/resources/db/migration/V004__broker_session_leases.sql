delete from broker_connection_states;

alter table broker_connection_states
    add column owner_session_hash varchar(64) not null;

alter table broker_connections
    add column owner_session_hash varchar(64),
    add column lease_expires_at timestamptz,
    add column revocation_started_at timestamptz;

update broker_connections
set owner_session_hash = repeat('0', 64),
    lease_expires_at = now();

alter table broker_connections
    alter column owner_session_hash set not null,
    alter column lease_expires_at set not null;

create index idx_broker_connections_expired_lease
    on broker_connections (lease_expires_at)
    where revocation_started_at is null;
