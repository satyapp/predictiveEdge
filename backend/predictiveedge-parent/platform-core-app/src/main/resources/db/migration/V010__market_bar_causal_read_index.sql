create index idx_market_bar_tenant_causal_replay
    on market_intelligence.market_bar_revision
       (user_id,broker_account_id,subject_type,subject_id,timeframe,
        interval_start,interval_end,available_at,revision desc);
