create table market_intelligence.pre_open_auction_snapshot (
    snapshot_id uuid primary key,
    user_id uuid not null references identity.app_user(user_id) on delete cascade,
    broker_account_id varchar(128) not null,
    venue varchar(32) not null,
    symbol varchar(128) not null,
    series varchar(16) not null,
    trading_date date not null,
    previous_close numeric(20,8) not null,
    auction_price numeric(20,8) not null,
    auction_volume bigint not null,
    auction_turnover numeric(24,4) not null,
    best_bid numeric(20,8) not null,
    best_ask numeric(20,8) not null,
    observed_at timestamptz not null,
    received_at timestamptz not null,
    evidence_hash char(64) not null,
    constraint chk_pre_open_positive_prices check (
        previous_close > 0 and auction_price > 0 and best_bid > 0 and best_ask > 0),
    constraint chk_pre_open_nonnegative_activity check (auction_volume >= 0 and auction_turnover >= 0),
    constraint chk_pre_open_book check (best_bid <= best_ask),
    constraint chk_pre_open_timestamps check (observed_at <= received_at),
    constraint chk_pre_open_hash check (evidence_hash ~ '^[0-9a-f]{64}$')
);

create index idx_pre_open_auction_point_in_time
    on market_intelligence.pre_open_auction_snapshot
       (user_id, broker_account_id, venue, symbol, trading_date, observed_at desc, received_at desc);
