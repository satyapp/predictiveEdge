alter table eventing.outbox_event
    add column destination_topic varchar(249);

update eventing.outbox_event
set destination_topic = broker_topic
where destination_topic is null and broker_topic is not null;

do $$
begin
    if exists (select 1 from eventing.outbox_event where destination_topic is null) then
        raise exception 'Existing outbox rows require a governed destination topic before V006';
    end if;
end $$;

alter table eventing.outbox_event
    alter column destination_topic set not null,
    add constraint chk_eventing_destination_topic
        check (destination_topic ~ '^[A-Za-z0-9._-]+$'
            and destination_topic not in ('.', '..'));
