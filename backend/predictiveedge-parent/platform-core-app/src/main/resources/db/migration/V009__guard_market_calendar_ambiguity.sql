create or replace function market_intelligence.reject_ambiguous_market_session()
returns trigger
language plpgsql
as $$
begin
    if exists (
        select 1
        from market_intelligence.market_session existing
        where existing.session_definition_id <> new.session_definition_id
          and existing.venue = new.venue
          and existing.valid_from = new.valid_from
          and tstzrange(existing.coverage_start, existing.coverage_end, '[)')
              && tstzrange(new.coverage_start, new.coverage_end, '[)')
    ) then
        raise exception 'ambiguous market session definition for venue % at %', new.venue, new.valid_from
            using errcode = '23P01';
    end if;
    return new;
end;
$$;

create trigger trg_reject_ambiguous_market_session
before insert on market_intelligence.market_session
for each row execute function market_intelligence.reject_ambiguous_market_session();

create or replace function market_intelligence.reject_overlapping_session_phase()
returns trigger
language plpgsql
as $$
begin
    if exists (
        select 1
        from market_intelligence.market_session_phase existing
        where existing.session_definition_id = new.session_definition_id
          and existing.starts_at <> new.starts_at
          and tstzrange(existing.starts_at, existing.ends_at, '[)')
              && tstzrange(new.starts_at, new.ends_at, '[)')
    ) then
        raise exception 'overlapping phase window for session definition %', new.session_definition_id
            using errcode = '23P01';
    end if;
    return new;
end;
$$;

create trigger trg_reject_overlapping_session_phase
before insert on market_intelligence.market_session_phase
for each row execute function market_intelligence.reject_overlapping_session_phase();
