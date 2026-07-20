create table broker_connection_states (
    state_hash varchar(64) primary key,
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_id varchar(32) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz
);

create index idx_broker_connection_states_expiry
    on broker_connection_states (expires_at) where consumed_at is null;

create table broker_connections (
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_id varchar(32) not null,
    external_account_id varchar(128) not null,
    encrypted_access_token text not null,
    connected_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (user_id, broker_id)
);
